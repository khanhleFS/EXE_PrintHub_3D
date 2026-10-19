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
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CheckoutOrderRepository;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.entity.PointTransaction;
import com.fpt.printhub_3d.entity.PointWallet;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.CloudinaryService;
import com.fpt.printhub_3d.service.CustomOrderService;
import com.fpt.printhub_3d.service.FileStorageService;
import com.fpt.printhub_3d.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomOrderServiceImpl implements CustomOrderService {

    private final CustomOrderRepository customOrderRepository;
    private final FileAssetRepository fileAssetRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final CloudinaryService cloudinaryService;
    private final CheckoutOrderRepository checkoutOrderRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final OrderRepository orderRepository;
    private final CheckoutRewardService checkoutRewardService;

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
                .unitPrice(saved.getUnitPrice())
                .quantity(saved.getQuantity())
                .priceVersion(saved.getPriceVersion())
                .rulerModel(saved.getRulerModel())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    private CustomOrderDetailResponseDTO toDTO(CustomOrder o) {
        Payment p = paymentRepository.findByCustomOrderId(o.getId()).orElse(null);
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
                .paymentMethod(o.getPaymentMethod())
                .paymentStatus(p != null ? p.getStatus() : "PENDING")
                .checkoutOrderId(o.getCheckoutOrder() != null ? o.getCheckoutOrder().getId() : null)
                .unitPrice(o.getUnitPrice())
                .priceVersion(o.getPriceVersion())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
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
        order.setPriceVersion(2);
        if (request.paymentMethod() != null && "COD".equalsIgnoreCase(request.paymentMethod())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn in 3D theo yêu cầu chỉ chấp nhận thanh toán trực tuyến qua PayOS (không hỗ trợ COD)");
        }
        order.setPaymentMethod("PAYOS");
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
        order.setUnitPrice(request.price());
        order.setProcessedBy(admin);
        order.setStatus("QUOTED");
        order.setUpdatedAt(Instant.now());

        if (order.getCheckoutOrder() != null) {
            com.fpt.printhub_3d.entity.CheckoutOrder parent = checkoutOrderRepository
                    .findLockedById(order.getCheckoutOrder().getId())
                    .orElse(order.getCheckoutOrder());
            parent.setCustomPricingVersion(parent.getCustomPricingVersion() != null ? parent.getCustomPricingVersion() + 1 : 2L);
            parent.setSummaryVersion(parent.getSummaryVersion() != null ? parent.getSummaryVersion() + 1 : 2L);
            parent.setUpdatedAt(Instant.now());

            order.setQuoteVersion(parent.getCustomPricingVersion());
            order.setPriceVersion(parent.getCustomPricingVersion().intValue());

            List<CustomOrder> siblings = customOrderRepository.findByCheckoutOrderId(parent.getId());
            boolean allQuoted = siblings.stream().allMatch(c -> c.getQuotedPrice() != null || "CANCELLED".equals(c.getStatus()));
            if (allQuoted) {
                parent.setCustomStatus("QUOTED");
                if ("PENDING_QUOTE".equals(parent.getStatus())) {
                    parent.setStatus("QUOTED");
                }
            }
            checkoutOrderRepository.save(parent);
        } else {
            long newQuoteVer = order.getQuoteVersion() != null ? order.getQuoteVersion() + 1 : 2L;
            order.setQuoteVersion(newQuoteVer);
            order.setPriceVersion((int) newQuoteVer);
        }
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

        String current = order.getStatus();
        String next = request.status();

        if ("ACCEPTED".equals(next)) {
            if (!order.getBuyer().getId().equals(user.getId())) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Chỉ người đặt mới có quyền duyệt báo giá");
            }
            if (!"QUOTED".equals(current)) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Yêu cầu in chưa có báo giá từ xưởng/hệ thống");
            }
            String method = request.paymentMethod();
            if (method == null || !"PAYOS".equalsIgnoreCase(method)) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn in 3D theo yêu cầu chỉ chấp nhận thanh toán trực tuyến qua PayOS (không hỗ trợ COD)");
            }
            order.setPaymentMethod("PAYOS");
        } else if ("CANCELLED".equals(next)) {
            if (!order.getBuyer().getId().equals(user.getId()) && user.getRole() != UserRole.ADMIN) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Bạn không có quyền hủy yêu cầu này");
            }
            if (!Set.of("REQUESTED", "QUOTED").contains(current)) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chỉ có thể hủy yêu cầu khi chưa bước vào sản xuất");
            }
        } else {
            if (user.getRole() != UserRole.ADMIN) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Chỉ quản trị viên mới có thể chuyển trạng thái sản xuất");
            }
            boolean isValidTransition = switch (current) {
                case "PAID" -> "PREPARING".equals(next) || "PRINTING".equals(next);
                case "PREPARING" -> "PRINTING".equals(next);
                case "PRINTING" -> "SHIPPING".equals(next);
                case "SHIPPING" -> "COMPLETED".equals(next);
                default -> false;
            };
            if (!isValidTransition) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chuyển trạng thái không hợp lệ từ " + current + " sang " + next);
            }
            if ("COMPLETED".equals(next) && "COD".equals(order.getPaymentMethod())) {
                Payment p = paymentRepository.findByCustomOrderId(id).orElseGet(Payment::new);
                p.setCustomOrder(order);
                p.setAmount(order.getQuotedPrice());
                p.setGateway("COD");
                p.setStatus("SUCCESS");
                p.setPaidAt(Instant.now());
                p.setCreatedAt(Instant.now());
                p.setUpdatedAt(Instant.now());
                paymentRepository.save(p);
            }
        }

        order.setStatus(next);
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        if ("COMPLETED".equals(next)) {
            awardCustomOrderRewards(order);
        }

        if (order.getCheckoutOrder() != null) {
            syncParentCheckoutOrder(order.getCheckoutOrder().getId());
        }

        notificationService.sendNotification(order.getBuyer(),
                "Cập nhật tiến trình in",
                "Yêu cầu in 3D " + id + " đã chuyển sang trạng thái: " + next,
                "CUSTOM_ORDER",
                "/quotations");
    }

    private static final BigDecimal REWARD_POINT_UNIT = new BigDecimal("10000");

    private void awardCustomOrderRewards(CustomOrder order) {
        if (order.getQuotedPrice() == null || pointWalletRepository == null || pointTransactionRepository == null) {
            return;
        }

        User buyer = order.getBuyer();
        if (buyer == null) return;

        // P1.6: Nếu thuộc CheckoutOrder, chuyển sang xử lý tích điểm lũy kế tập trung có khóa cha
        if (order.getCheckoutOrder() != null) {
            checkoutRewardService.rewardCheckoutOrderCumulative(order.getCheckoutOrder().getId(), buyer);
            return;
        }

        // Đơn custom standalone: Kiểm tra thanh toán và chống cấp trùng
        String desc = "Hoàn thành yêu cầu in 3D " + order.getId();
        if (pointTransactionRepository.existsByPointWalletUserIdAndDescription(buyer.getId(), desc)) {
            return;
        }

        Payment payment = paymentRepository.findByCustomOrderId(order.getId()).orElse(null);
        if (payment == null || !"SUCCESS".equals(payment.getStatus())) {
            log.info("Chưa cộng điểm cho CustomOrder đơn lẻ {} vì thanh toán chưa hoàn tất", order.getId());
            return;
        }

        int q = (order.getQuantity() != null && order.getQuantity() > 0) ? order.getQuantity() : 1;
        BigDecimal eligibleAmount = (order.getPriceVersion() != null && order.getPriceVersion() >= 2)
                ? order.getQuotedPrice().multiply(BigDecimal.valueOf(q))
                : order.getQuotedPrice();

        int earnedPoints = eligibleAmount.divideToIntegralValue(REWARD_POINT_UNIT).intValue();
        if (earnedPoints <= 0) {
            return;
        }

        PointWallet wallet = pointWalletRepository.findById(buyer.getId()).orElseGet(() -> {
            PointWallet newWallet = PointWallet.builder()
                    .userId(buyer.getId())
                    .user(buyer)
                    .balance(0)
                    .isNew(true)
                    .build();
            buyer.setPointWallet(newWallet);
            return newWallet;
        });

        wallet.setBalance(wallet.getBalance() + earnedPoints);
        wallet.setUpdatedAt(java.time.LocalDateTime.now());
        wallet = pointWalletRepository.save(wallet);
        buyer.setPointWallet(wallet);

        pointTransactionRepository.save(PointTransaction.builder()
                .pointWallet(wallet)
                .amount(earnedPoints)
                .type(PointTransactionType.EARN)
                .description(desc)
                .createdAt(java.time.LocalDateTime.now())
                .build());

        log.info("Đã tích {} điểm cho buyer {} từ CustomOrder đơn lẻ {}", earnedPoints, buyer.getId(), order.getId());
    }

    private void syncParentCheckoutOrder(UUID checkoutOrderId) {
        if (checkoutOrderId == null || checkoutOrderRepository == null) return;
        checkoutOrderRepository.findById(checkoutOrderId).ifPresent(parent -> {
            List<Order> childOrders = (orderRepository != null)
                    ? orderRepository.findByCheckoutOrderId(checkoutOrderId) : Collections.emptyList();
            List<CustomOrder> childCustoms = customOrderRepository.findByCheckoutOrderId(checkoutOrderId);

            boolean allOrdersDone = childOrders.stream().allMatch(o -> "COMPLETED".equals(o.getStatus()) || "CANCELLED".equals(o.getStatus()));
            boolean allCustomsDone = childCustoms.stream().allMatch(c -> "COMPLETED".equals(c.getStatus()) || "CANCELLED".equals(c.getStatus()));

            if (allOrdersDone && allCustomsDone && (!childOrders.isEmpty() || !childCustoms.isEmpty())) {
                parent.setStatus("COMPLETED");
            } else {
                boolean anyShipping = childOrders.stream().anyMatch(o -> "SHIPPING".equals(o.getStatus()))
                        || childCustoms.stream().anyMatch(c -> "SHIPPING".equals(c.getStatus()));
                if (anyShipping && !"COMPLETED".equals(parent.getStatus())) {
                    parent.setStatus("SHIPPING");
                }
            }
            parent.setUpdatedAt(Instant.now());
            checkoutOrderRepository.save(parent);
        });
    }
}
