package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.CustomPrintErrorCode;
import com.fpt.printhub_3d.common.exception.VaultErrorCode;
import com.fpt.printhub_3d.dto.custom_prints.*;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Enumeration.CustomOrderStatus;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.FileAsset;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.FileAssetRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import com.fpt.printhub_3d.service.CloudinaryService;
import com.fpt.printhub_3d.service.CustomOrderService;
import com.fpt.printhub_3d.service.FileStorageService;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.OrderTransitionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomOrderServiceImpl implements CustomOrderService {

    private final CustomOrderRepository customOrderRepository;
    private final FileAssetRepository fileAssetRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CloudinaryService cloudinaryService;
    private final com.fpt.printhub_3d.service.OrderTransitionPolicy orderTransitionPolicy;

    private User getCurrentUserSafely() {
        try {
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof com.fpt.printhub_3d.common.security.CustomUserDetail userDetail) {
                return userDetail.getUser();
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public CustomOrderResponseDTO createRequest(String requirements, MultipartFile file, User buyer) {
        log.info("Buyer [{}] đang tạo yêu cầu in custom", buyer.getId());

        String attachmentUrl = cloudinaryService.uploadRawFile(file, "printhub3d/custom_prints");

        CustomOrder customOrder = new CustomOrder();
        customOrder.setBuyer(buyer);
        customOrder.setProcessedBy(null);
        customOrder.setRequirements(requirements);
        customOrder.setAttachmentUrl(attachmentUrl);
        customOrder.setStatus(CustomOrderStatus.REQUESTED.name());
        customOrder.setCreatedAt(Instant.now());
        customOrder.setUpdatedAt(Instant.now());

        CustomOrder saved = customOrderRepository.save(customOrder);
        log.info("Tạo yêu cầu in custom thành công. ID đơn: {}", saved.getId());

        return CustomOrderResponseDTO.builder()
                .id(saved.getId())
                .buyerId(saved.getBuyer().getId())
                .buyerName(saved.getBuyer().getFullName())
                .processedById(saved.getProcessedBy() != null ? saved.getProcessedBy().getId() : null)
                .processedByName(saved.getProcessedBy() != null ? saved.getProcessedBy().getFullName() : null)
                .requirements(saved.getRequirements())
                .attachmentUrl(saved.getAttachmentUrl())
                .quotedPrice(saved.getQuotedPrice())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    private CustomOrderDetailResponseDTO toDTO(CustomOrder o) {
        Payment p = paymentRepository.findByCustomOrderId(o.getId()).orElse(null);
        if (p == null && o.getCheckoutOrder() != null) {
            p = paymentRepository.findByCheckoutOrderId(o.getCheckoutOrder().getId()).orElse(null);
        }

        String pMethod = o.getPaymentMethod() != null ? o.getPaymentMethod() : (p != null ? p.getGateway() : "PAYOS");
        String pStatus = p != null ? p.getStatus() : "PENDING";

        User current = getCurrentUserSafely();
        boolean isAdmin = current != null && current.getRole() == UserRole.ADMIN;
        boolean isOwner = current != null && o.getBuyer() != null && current.getId().equals(o.getBuyer().getId());
        if (current == null) {
            isAdmin = true;
        }

        OrderTransitionPolicy.TransitionEvaluation eval = orderTransitionPolicy.evaluateActions(
                OrderTransitionPolicy.OrderType.CUSTOM,
                o.getStatus(),
                pMethod,
                pStatus,
                isAdmin,
                isOwner
        );

        return CustomOrderDetailResponseDTO.builder()
                .id(o.getId())
                .buyerId(o.getBuyer().getId())
                .buyerName(o.getBuyer().getFullName())
                .processedById(o.getProcessedBy() != null ? o.getProcessedBy().getId() : null)
                .processedByName(o.getProcessedBy() != null ? o.getProcessedBy().getFullName() : null)
                .requirements(o.getRequirements())
                .quantity(o.getQuantity() != null ? o.getQuantity() : 1)
                .shippingAddress(o.getShippingAddress())
                .attachmentUrl(o.getAttachmentUrl())
                .quotedPrice(o.getQuotedPrice())
                .status(o.getStatus())
                .rulerModel(o.getRulerModel())
                .customName(o.getCustomName())
                .customStudentId(o.getCustomStudentId())
                .color(o.getColor())
                .fontStyle(o.getFontStyle())
                .paymentMethod(pMethod)
                .paymentStatus(pStatus)
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .allowedActions(eval.allowedActions())
                .blockedActions(eval.blockedActions())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomOrderDetailResponseDTO> getMyCustomOrders(User user) {
        return customOrderRepository.findByBuyerIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomOrderDetailResponseDTO> getAllCustomOrders() {
        return customOrderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public CustomOrderDetailResponseDTO createCustomOrder(User user, CustomOrderCreateRequestDTO request) {
        String attachmentUrl = null;
        if (request.attachmentUrl() != null && !request.attachmentUrl().isBlank()) {
            attachmentUrl = request.attachmentUrl();
        } else if (request.fileId() != null) {
            FileAsset file = fileAssetRepository.findByIdAndDeletedFalse(request.fileId())
                    .orElseThrow(() -> new ApiException(VaultErrorCode.FILE_NOT_FOUND, "Không tìm thấy file thiết kế"));

            if (!file.getOwner().getId().equals(user.getId()) && user.getRole() != UserRole.ADMIN) {
                throw new ApiException(VaultErrorCode.FORBIDDEN_FILE_ACCESS);
            }
            attachmentUrl = "/api/vault/files/" + file.getId() + "/download";
        } else {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vui lòng đính kèm file 3D (attachmentUrl hoặc fileId)");
        }

        CustomOrder order = new CustomOrder();
        order.setBuyer(user);
        order.setAttachmentUrl(attachmentUrl);
        order.setRequirements(request.requirements());
        order.setQuantity(request.quantity());
        order.setShippingAddress(request.shippingAddress());
        order.setRulerModel(request.rulerModel());
        order.setCustomName(request.customName());
        order.setCustomStudentId(request.customStudentId());
        order.setColor(request.color());
        order.setFontStyle(request.fontStyle());
        order.setPaymentMethod(request.paymentMethod());
        order.setStatus("REQUESTED");
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        customOrderRepository.save(order);
        return toDTO(order);
    }

    @Override
    public void quoteCustomOrder(UUID id, CustomOrderQuoteRequestDTO request, User admin) {
        CustomOrder order = customOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu in"));

        if (!Set.of("REQUESTED", "QUOTED").contains(order.getStatus())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Không thể sửa báo giá của yêu cầu đã được chấp nhận");
        }

        order.setQuotedPrice(request.price());
        order.setProcessedBy(admin);
        order.setStatus("QUOTED");
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        notificationService.sendNotification(order.getBuyer(),
                "Đã có báo giá yêu cầu in",
                "Yêu cầu in 3D " + id + " đã được báo giá: " + request.price() + " VND.",
                "CUSTOM_ORDER",
                "/quotations");
    }

    @Override
    public void updateCustomOrderStatus(UUID id, CustomOrderStatusRequestDTO request, User user) {
        CustomOrder order = customOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu in"));

        Payment p = paymentRepository.findByCustomOrderId(id).orElse(null);
        if (p == null && order.getCheckoutOrder() != null) {
            p = paymentRepository.findByCheckoutOrderId(order.getCheckoutOrder().getId()).orElse(null);
        }

        String paymentMethod = order.getPaymentMethod() != null ? order.getPaymentMethod() : (p != null ? p.getGateway() : "PAYOS");
        String paymentStatus = p != null ? p.getStatus() : "PENDING";

        String target = request.getEffectiveTarget();
        String resolvedStatus = orderTransitionPolicy.validateAndResolveNextStatus(
                OrderTransitionPolicy.OrderType.CUSTOM,
                order.getStatus(),
                target,
                paymentMethod,
                paymentStatus,
                user,
                order.getBuyer().getId()
        );

        if ("ACCEPTED".equals(resolvedStatus)) {
            String method = request.paymentMethod();
            if (method == null || !Set.of("COD", "PAYOS").contains(method.toUpperCase())) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vui lòng chọn phương thức thanh toán COD hoặc PAYOS");
            }
            order.setPaymentMethod(method.toUpperCase());
        } else if ("COMPLETED".equals(resolvedStatus) && "COD".equalsIgnoreCase(paymentMethod)) {
            Payment codPay = p != null ? p : new Payment();
            codPay.setCustomOrder(order);
            BigDecimal total = order.getQuotedPrice() != null
                    ? order.getQuotedPrice().multiply(BigDecimal.valueOf(order.getQuantity() != null ? order.getQuantity() : 1))
                    : BigDecimal.ZERO;
            codPay.setAmount(total);
            codPay.setGateway("COD");
            codPay.setStatus("SUCCESS");
            codPay.setPaidAt(Instant.now());
            if (codPay.getCreatedAt() == null) codPay.setCreatedAt(Instant.now());
            codPay.setUpdatedAt(Instant.now());
            paymentRepository.save(codPay);
        }

        order.setStatus(resolvedStatus);
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        notificationService.sendNotification(order.getBuyer(),
                "Cập nhật tiến trình in",
                "Yêu cầu in 3D " + id + " đã chuyển sang trạng thái: " + resolvedStatus,
                "CUSTOM_ORDER",
                "/quotations");
    }
}
