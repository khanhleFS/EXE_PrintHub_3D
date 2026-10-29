package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.entity.Enumeration.MovementType;
import com.fpt.printhub_3d.entity.Enumeration.ReservationState;
import com.fpt.printhub_3d.entity.InventoryMovement;
import com.fpt.printhub_3d.entity.InventoryReservation;
import com.fpt.printhub_3d.entity.Product;
import com.fpt.printhub_3d.repository.InventoryMovementRepository;
import com.fpt.printhub_3d.repository.InventoryReservationRepository;
import com.fpt.printhub_3d.repository.ProductRepository;
import com.fpt.printhub_3d.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryMovementRepository inventoryMovementRepository;

    @Override
    @Transactional
    public void reserveForOrder(UUID orderId, Map<UUID, Integer> productQuantities, String operationId, Instant expiresAt) {
        log.info("Thực hiện giữ chỗ tồn kho cho đơn [{}] với operationId [{}]", orderId, operationId);

        // Khóa products theo thứ tự UUID tăng dần để tránh deadlock khi có nhiều request đồng thời
        List<UUID> sortedProductIds = productQuantities.keySet().stream().sorted().toList();

        for (UUID productId : sortedProductIds) {
            int requestedQty = productQuantities.get(productId);
            if (requestedQty <= 0) continue;

            Product product = productRepository.findLockedById(productId)
                    .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm ID: " + productId));

            Optional<InventoryReservation> existingOpt = inventoryReservationRepository.findByOrderIdAndProductId(orderId, productId);
            if (existingOpt.isPresent()) {
                InventoryReservation existing = existingOpt.get();
                if (existing.getState() == ReservationState.ACTIVE || existing.getState() == ReservationState.CONFIRMED) {
                    log.info("Đơn [{}] đã có reservation hợp lệ cho sản phẩm [{}]. Replay idempotent.", orderId, productId);
                    continue;
                }
            }

            if (product.getStock() < requestedQty) {
                log.warn("Sản phẩm [{}] không đủ tồn kho. Hiện còn: {}, Yêu cầu: {}", product.getTitle(), product.getStock(), requestedQty);
                throw new ApiException(OrderErrorCode.OUT_OF_STOCK,
                        "Sản phẩm '" + product.getTitle() + "' không đủ số lượng tồn kho (còn: " + product.getStock() + ", yêu cầu: " + requestedQty + ")");
            }

            // Trừ stock khả dụng đúng 1 lần
            product.setStock(product.getStock() - requestedQty);
            productRepository.save(product);

            InventoryReservation reservation = InventoryReservation.builder()
                    .orderId(orderId)
                    .productId(productId)
                    .quantity(requestedQty)
                    .state(ReservationState.ACTIVE)
                    .reservedAt(Instant.now())
                    .expiresAt(expiresAt)
                    .source("CHECKOUT")
                    .operationId(operationId)
                    .build();
            reservation = inventoryReservationRepository.save(reservation);

            InventoryMovement movement = InventoryMovement.builder()
                    .reservationId(reservation.getId())
                    .operationType(MovementType.RESERVE)
                    .delta(-requestedQty)
                    .availableAfter(product.getStock())
                    .actorOrOperationId(operationId)
                    .createdAt(Instant.now())
                    .build();
            inventoryMovementRepository.save(movement);

            log.info("Đã giữ chỗ [{}] sản phẩm [{}] cho đơn [{}]. Tồn kho khả dụng còn: {}", requestedQty, product.getId(), orderId, product.getStock());
        }
    }

    @Override
    @Transactional
    public void confirmReservation(UUID orderId) {
        log.info("Xác nhận giữ chỗ (ACTIVE -> CONFIRMED) cho đơn [{}]", orderId);
        List<InventoryReservation> reservations = inventoryReservationRepository.findLockedByOrderId(orderId);

        for (InventoryReservation r : reservations) {
            if (r.getState() == ReservationState.ACTIVE) {
                r.setState(ReservationState.CONFIRMED);
                r.setConfirmedAt(Instant.now());
                inventoryReservationRepository.save(r);
                log.info("Reservation [{}] của sản phẩm [{}] đơn [{}] đã chuyển sang CONFIRMED. Tồn kho không đổi.", r.getId(), r.getProductId(), orderId);
            } else if (r.getState() == ReservationState.CONFIRMED) {
                log.debug("Reservation [{}] đã ở trạng thái CONFIRMED từ trước. Bỏ qua.", r.getId());
            } else {
                log.warn("Reservation [{}] ở trạng thái [{}] không thể confirm.", r.getId(), r.getState());
            }
        }
    }

    @Override
    @Transactional
    public void releaseActiveReservations(UUID orderId, String reason, String operationId) {
        log.info("Giải phóng giữ chỗ tồn kho (ACTIVE -> RELEASED) cho đơn [{}] với lý do: {}", orderId, reason);
        List<InventoryReservation> reservations = inventoryReservationRepository.findLockedByOrderId(orderId);

        List<InventoryReservation> activeReservations = reservations.stream()
                .filter(r -> r.getState() == ReservationState.ACTIVE)
                .sorted(Comparator.comparing(InventoryReservation::getProductId))
                .toList();

        if (activeReservations.isEmpty()) {
            log.info("Không có reservation ACTIVE nào cần giải phóng cho đơn [{}]", orderId);
            return;
        }

        for (InventoryReservation r : activeReservations) {
            Product product = productRepository.findLockedById(r.getProductId())
                    .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm ID: " + r.getProductId()));

            // Hoàn lại stock khả dụng đúng 1 lần
            product.setStock(product.getStock() + r.getQuantity());
            productRepository.save(product);

            r.setState(ReservationState.RELEASED);
            r.setReleasedAt(Instant.now());
            r.setReleaseReason(reason);
            inventoryReservationRepository.save(r);

            InventoryMovement movement = InventoryMovement.builder()
                    .reservationId(r.getId())
                    .operationType(MovementType.RELEASE)
                    .delta(r.getQuantity())
                    .availableAfter(product.getStock())
                    .actorOrOperationId(operationId)
                    .createdAt(Instant.now())
                    .build();
            inventoryMovementRepository.save(movement);

            log.info("Đã hoàn lại [{}] sản phẩm [{}] về kho cho đơn [{}]. Tồn kho mới: {}", r.getQuantity(), product.getId(), orderId, product.getStock());
        }
    }
}
