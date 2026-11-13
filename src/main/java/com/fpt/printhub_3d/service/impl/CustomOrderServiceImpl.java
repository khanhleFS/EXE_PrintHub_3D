package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CheckoutOrderErrorCode;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.CustomPrintErrorCode;
import com.fpt.printhub_3d.common.exception.VaultErrorCode;
import com.fpt.printhub_3d.dto.custom_prints.*;
import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Enumeration.CustomOrderStatus;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.FileAsset;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CheckoutOrderRepository;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.FileAssetRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.repository.StoreSettingRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import com.fpt.printhub_3d.service.CloudinaryService;
import com.fpt.printhub_3d.service.CustomOrderService;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.entity.PointTransaction;
import com.fpt.printhub_3d.entity.PointWallet;
import com.fpt.printhub_3d.repository.PointTransactionRepository;
import com.fpt.printhub_3d.repository.PointWalletRepository;
import java.time.LocalDateTime;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.OrderTransitionPolicy;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class CustomOrderServiceImpl implements CustomOrderService {

    private final CustomOrderRepository customOrderRepository;
    private final FileAssetRepository fileAssetRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CloudinaryService cloudinaryService;
    private final com.fpt.printhub_3d.service.OrderTransitionPolicy orderTransitionPolicy;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final CheckoutOrderRepository checkoutOrderRepository;
    private final OrderRepository orderRepository;
    private final StoreSettingRepository storeSettingRepository;
    private final SubscriptionBenefitService subscriptionBenefitService;
    private static final BigDecimal REWARD_POINT_UNIT = new BigDecimal("10000");
    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("30000");

    @Autowired
    public CustomOrderServiceImpl(
            CustomOrderRepository customOrderRepository,
            FileAssetRepository fileAssetRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            CloudinaryService cloudinaryService,
            com.fpt.printhub_3d.service.OrderTransitionPolicy orderTransitionPolicy,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository,
            CheckoutOrderRepository checkoutOrderRepository,
            OrderRepository orderRepository,
            StoreSettingRepository storeSettingRepository,
            @Autowired(required = false) SubscriptionBenefitService subscriptionBenefitService
    ) {
        this.customOrderRepository = customOrderRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.cloudinaryService = cloudinaryService;
        this.orderTransitionPolicy = orderTransitionPolicy;
        this.pointWalletRepository = pointWalletRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.checkoutOrderRepository = checkoutOrderRepository;
        this.orderRepository = orderRepository;
        this.storeSettingRepository = storeSettingRepository;
        this.subscriptionBenefitService = subscriptionBenefitService;
    }

    public CustomOrderServiceImpl(
            CustomOrderRepository customOrderRepository,
            FileAssetRepository fileAssetRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            CloudinaryService cloudinaryService,
            com.fpt.printhub_3d.service.OrderTransitionPolicy orderTransitionPolicy,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository,
            CheckoutOrderRepository checkoutOrderRepository,
            OrderRepository orderRepository,
            StoreSettingRepository storeSettingRepository
    ) {
        this(
                customOrderRepository,
                fileAssetRepository,
                paymentRepository,
                userRepository,
                notificationService,
                cloudinaryService,
                orderTransitionPolicy,
                pointWalletRepository,
                pointTransactionRepository,
                checkoutOrderRepository,
                orderRepository,
                storeSettingRepository,
                null
        );
    }

    public CustomOrderServiceImpl(
            CustomOrderRepository customOrderRepository,
            FileAssetRepository fileAssetRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            CloudinaryService cloudinaryService,
            com.fpt.printhub_3d.service.OrderTransitionPolicy orderTransitionPolicy,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository
    ) {
        this(
                customOrderRepository,
                fileAssetRepository,
                paymentRepository,
                userRepository,
                notificationService,
                cloudinaryService,
                orderTransitionPolicy,
                pointWalletRepository,
                pointTransactionRepository,
                null,
                null,
                null
        );
    }

    private BigDecimal resolveLineTotal(CustomOrder order) {
        if (order == null) return BigDecimal.ZERO;
        if (order.getQuotedPrice() != null && order.getQuotedPrice().compareTo(BigDecimal.ZERO) > 0) {
            return order.getQuotedPrice();
        }
        int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        if (order.getUnitQuotedPrice() != null && order.getUnitQuotedPrice().compareTo(BigDecimal.ZERO) > 0) {
            return order.getUnitQuotedPrice().multiply(BigDecimal.valueOf(qty));
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveDefaultShippingFee() {
        if (storeSettingRepository != null) {
            try {
                var feeOpt = storeSettingRepository.findById("shippingFee");
                if (feeOpt.isPresent() && feeOpt.get().getValue() != null) {
                    BigDecimal configured = new BigDecimal(feeOpt.get().getValue().trim());
                    if (configured.compareTo(BigDecimal.ZERO) > 0) {
                        return configured;
                    }
                }
            } catch (Exception ignored) {}
        }
        return DEFAULT_SHIPPING_FEE;
    }

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
                .shippingAddress(saved.getShippingAddress())
                .cancelReason(saved.getCancelReason())
                .cancelledByRole(saved.getCancelledByRole())
                .cancelledAt(saved.getCancelledAt())
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
                .unitQuotedPrice(o.getUnitQuotedPrice())
                .quotedPrice(o.getQuotedPrice())
                .quoteNote(o.getQuoteNote())
                .quotedAt(o.getQuotedAt())
                .checkoutOrderId(o.getCheckoutOrder() != null ? o.getCheckoutOrder().getId() : null)
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
                .subtotalAmount(o.getSubtotalAmount())
                .shippingFee(o.getShippingFee())
                .discountAmount(o.getDiscountAmount())
                .shippingDiscountAmount(o.getShippingDiscountAmount())
                .appliedSubscriptionId(o.getAppliedSubscriptionId())
                .appliedSubscriptionName(o.getAppliedSubscriptionName())
                .priorityProduction(o.getPriorityProduction())
                .cancelReason(o.getCancelReason())
                .cancelledByRole(o.getCancelledByRole())
                .cancelledAt(o.getCancelledAt())
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
    @Transactional(readOnly = true)
    public CustomOrderDetailResponseDTO getCustomOrderDetail(UUID id, User currentUser) {
        CustomOrder order = customOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy yêu cầu in ID: " + id));
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        boolean isOwner = currentUser != null && order.getBuyer() != null
                && order.getBuyer().getId().equals(currentUser.getId());
        if (!isAdmin && !isOwner) {
            throw new ApiException(CommonErrorCode.FORBIDDEN,
                    "Bạn không có quyền xem yêu cầu in này");
        }
        return toDTO(order);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomOrderDetailResponseDTO getAdminCustomOrderDetail(UUID id) {
        CustomOrder order = customOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy yêu cầu in ID: " + id));
        return toDTO(order);
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
        // Đơn hàng in tùy chỉnh bắt buộc sử dụng thanh toán trực tuyến PayOS
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

        BigDecimal unitPrice = request != null ? request.getEffectiveUnitPrice() : null;
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn giá sản phẩm phải lớn hơn 0 VND");
        }

        CheckoutOrder parent = null;
        if (order.getCheckoutOrder() != null && checkoutOrderRepository != null) {
            parent = checkoutOrderRepository.findLockedById(order.getCheckoutOrder().getId())
                    .orElse(order.getCheckoutOrder());
            if (!Set.of("PENDING_QUOTE", "QUOTED").contains(parent.getStatus())) {
                throw new ApiException(
                        CheckoutOrderErrorCode.INVALID_ORDER_TRANSITION,
                        "Không thể báo giá lại khi đơn cha đã chuyển sang trạng thái " + parent.getStatus()
                );
            }
        }

        int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
        String normalizedNote = request.note() != null && !request.note().isBlank() ? request.note().trim() : null;
        Instant now = Instant.now();

        order.setUnitQuotedPrice(unitPrice);
        order.setQuotedPrice(lineTotal);
        order.setQuoteNote(normalizedNote);
        order.setQuotedAt(now);
        order.setProcessedBy(admin);
        order.setStatus("QUOTED");
        order.setUpdatedAt(now);
        customOrderRepository.save(order);

        if (parent != null) {
            List<CustomOrder> siblings = new ArrayList<>(customOrderRepository.findByCheckoutOrderId(parent.getId()));
            if (siblings.stream().noneMatch(c -> id.equals(c.getId()))) {
                siblings.add(order);
            }
            List<CustomOrder> activeSiblings = siblings.stream()
                    .filter(c -> !"CANCELLED".equalsIgnoreCase(c.getStatus()))
                    .toList();

            BigDecimal customSubtotal = activeSiblings.stream()
                    .map(c -> id.equals(c.getId()) ? lineTotal : resolveLineTotal(c))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal marketSubtotal = orderRepository != null
                    ? orderRepository.findByCheckoutOrderId(parent.getId()).stream()
                            .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : BigDecimal.ZERO;

            BigDecimal itemsSubtotal = customSubtotal.add(marketSubtotal);
            boolean allQuoted = !activeSiblings.isEmpty() && activeSiblings.stream()
                    .allMatch(c -> {
                        BigDecimal lTotal = id.equals(c.getId()) ? lineTotal : resolveLineTotal(c);
                        String st = id.equals(c.getId()) ? "QUOTED" : c.getStatus();
                        return lTotal.compareTo(BigDecimal.ZERO) > 0 && !"REQUESTED".equalsIgnoreCase(st);
                    });

            parent.setItemsSubtotal(itemsSubtotal);
            if (allQuoted) {
                BigDecimal shippingFee = (parent.getShippingFee() != null && parent.getShippingFee().compareTo(BigDecimal.ZERO) > 0)
                        ? parent.getShippingFee()
                        : resolveDefaultShippingFee();
                parent.setShippingFee(shippingFee);
                parent.setTotalAmount(itemsSubtotal.add(shippingFee));
                if ("PENDING_QUOTE".equalsIgnoreCase(parent.getStatus())) {
                    parent.setStatus("QUOTED");
                }
            } else {
                BigDecimal currentShip = parent.getShippingFee() != null ? parent.getShippingFee() : BigDecimal.ZERO;
                parent.setTotalAmount(itemsSubtotal.add(currentShip));
            }
            parent.setSummaryVersion((parent.getSummaryVersion() != null ? parent.getSummaryVersion() : 1L) + 1L);
            parent.setUpdatedAt(now);
            checkoutOrderRepository.save(parent);
        }

        if (notificationService != null && order.getBuyer() != null) {
            String targetLink = parent != null ? "/orders/" + parent.getId() : "/quotations";
            notificationService.sendNotification(order.getBuyer(),
                    "Đã có báo giá yêu cầu in",
                    "Yêu cầu in 3D " + id + " đã được báo giá: " + unitPrice + " VND/chiếc (SL " + qty + ", thành tiền " + lineTotal + " VND).",
                    "CUSTOM_ORDER",
                    targetLink);
        }
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
            if (method == null || !"PAYOS".equalsIgnoreCase(method)) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn in 3D cá nhân hóa chỉ hỗ trợ thanh toán trực tuyến PayOS.");
            }
            order.setPaymentMethod("PAYOS");
            if (order.getCheckoutOrder() == null) {
                BigDecimal lineSub = resolveLineTotal(order);
                BigDecimal baseShip = order.getShippingFee() != null ? order.getShippingFee() : resolveDefaultShippingFee();
                if (subscriptionBenefitService != null) {
                    SubscriptionBenefitService.AppliedBenefitSnapshot snap = subscriptionBenefitService.reserveAndApplyBenefits(
                            order.getBuyer(),
                            SubscriptionBenefitService.UsageTargetType.CUSTOM_ORDER,
                            order.getId(),
                            SubscriptionBenefitService.BenefitScope.CUSTOM,
                            lineSub,
                            baseShip
                    );
                    order.setSubtotalAmount(snap.itemsSubtotal());
                    order.setShippingFee(snap.baseShippingFee());
                    order.setDiscountAmount(snap.discountAmount());
                    order.setShippingDiscountAmount(snap.shippingDiscountAmount());
                    order.setAppliedSubscriptionId(snap.appliedSubscriptionId());
                    order.setAppliedSubscriptionName(snap.appliedSubscriptionName());
                    order.setPriorityProduction(snap.priorityProduction());
                } else {
                    order.setSubtotalAmount(lineSub);
                    order.setShippingFee(BigDecimal.ZERO);
                    order.setDiscountAmount(BigDecimal.ZERO);
                    order.setShippingDiscountAmount(BigDecimal.ZERO);
                    order.setPriorityProduction(false);
                }
            }
        } else if ("CANCELLED".equals(resolvedStatus)) {
            boolean isAdminActor = user != null && user.getRole() == UserRole.ADMIN;
            String reason = request.getEffectiveCancelReason();
            if (isAdminActor) {
                if (reason == null || reason.length() < 5) {
                    throw new ApiException(CommonErrorCode.INVALID_INPUT,
                            "Vui lòng nhập lý do hủy đơn (tối thiểu 5 ký tự).");
                }
            } else if (reason == null || reason.isBlank()) {
                reason = "Khách hàng tự hủy yêu cầu";
            }
            order.setCancelReason(reason);
            order.setCancelledByRole(isAdminActor ? "ADMIN" : "BUYER");
            if (user != null) {
                order.setCancelledById(user.getId());
            }
            order.setCancelledAt(Instant.now());
            if (order.getCheckoutOrder() == null && subscriptionBenefitService != null) {
                subscriptionBenefitService.releaseReservedBenefits(
                        SubscriptionBenefitService.UsageTargetType.CUSTOM_ORDER, order.getId());
            }
        } else if ("COMPLETED".equals(resolvedStatus) && "COD".equalsIgnoreCase(paymentMethod)) {
            Payment codPay = p != null ? p : new Payment();
            codPay.setCustomOrder(order);
            BigDecimal total = resolveLineTotal(order);
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

        if ("COMPLETED".equals(resolvedStatus)) {
            try {
                completeRewards(id);
            } catch (Exception e) {
                log.warn("Lỗi khi tự động thưởng điểm cho custom order {}: {}", id, e.getMessage());
            }
        }

        if (notificationService != null && order.getBuyer() != null) {
            boolean isAdminActor = user != null && user.getRole() == UserRole.ADMIN;
            if ("CANCELLED".equals(resolvedStatus) && isAdminActor) {
                String shortId = id.toString().length() >= 8 ? id.toString().substring(0, 8) : id.toString();
                notificationService.sendNotification(order.getBuyer(),
                        "Yêu cầu báo giá đã bị hủy",
                        "Yêu cầu báo giá #" + shortId + " đã bị xưởng hủy. Lý do: " + order.getCancelReason(),
                        "CUSTOM_ORDER",
                        "/quotations");
            } else {
                notificationService.sendNotification(order.getBuyer(),
                        "Cập nhật tiến trình in",
                        "Yêu cầu in 3D " + id + " đã chuyển sang trạng thái: " + resolvedStatus,
                        "CUSTOM_ORDER",
                        "/quotations");
            }
        }
    }

    @Override
    @Transactional
    public RewardCompletionResponseDTO completeRewards(UUID customOrderId) {
        CustomOrder order = customOrderRepository.findLockedById(customOrderId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu in ID: " + customOrderId));

        if (!"COMPLETED".equals(order.getStatus())) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Yêu cầu in chưa ở trạng thái COMPLETED");
        }

        Payment payment = paymentRepository.findByCustomOrderId(customOrderId).orElse(null);
        if (payment == null && order.getCheckoutOrder() != null) {
            payment = paymentRepository.findByCheckoutOrderId(order.getCheckoutOrder().getId()).orElse(null);
        }
        if (payment == null || !"SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Yêu cầu in chưa hoàn tất thanh toán hợp lệ để nhận điểm thưởng");
        }

        User buyer = order.getBuyer();
        if (Boolean.TRUE.equals(order.getRewardProcessed())) {
            return RewardCompletionResponseDTO.builder()
                    .orderId(order.getId())
                    .customerId(buyer.getId())
                    .customerName(buyer.getFullName())
                    .rewardPointsEarned(order.getRewardPointsEarned() != null ? order.getRewardPointsEarned() : 0)
                    .totalRewardPoints(buyer.getRewardPoints() != null ? buyer.getRewardPoints() : 0)
                    .alreadyProcessed(true)
                    .build();
        }

        BigDecimal baseSub = order.getSubtotalAmount() != null ? order.getSubtotalAmount() : resolveLineTotal(order);
        BigDecimal disc = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal eligibleNet = baseSub.subtract(disc).max(BigDecimal.ZERO);
        int earnedPoints = eligibleNet.compareTo(BigDecimal.ZERO) > 0
                ? eligibleNet.divideToIntegralValue(REWARD_POINT_UNIT).intValue()
                : 0;

        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(buyer.getId()).orElseGet(() -> {
            PointWallet newWallet = PointWallet.builder()
                    .userId(buyer.getId())
                    .user(buyer)
                    .balance(0)
                    .isNew(true)
                    .updatedAt(LocalDateTime.now())
                    .build();
            buyer.setPointWallet(newWallet);
            return pointWalletRepository.save(newWallet);
        });
        wallet.setBalance((wallet.getBalance() != null ? wallet.getBalance() : 0) + earnedPoints);
        wallet.setUpdatedAt(LocalDateTime.now());
        wallet = pointWalletRepository.save(wallet);
        buyer.setPointWallet(wallet);

        if (earnedPoints > 0) {
            pointTransactionRepository.save(PointTransaction.builder()
                    .pointWallet(wallet)
                    .amount(earnedPoints)
                    .type(PointTransactionType.EARN)
                    .description("Thưởng hoàn thành yêu cầu in 3D #" + order.getId())
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        order.setRewardProcessed(true);
        order.setRewardPointsEarned(earnedPoints);
        order.setUpdatedAt(Instant.now());
        customOrderRepository.save(order);

        return RewardCompletionResponseDTO.builder()
                .orderId(order.getId())
                .customerId(buyer.getId())
                .customerName(buyer.getFullName())
                .rewardPointsEarned(earnedPoints)
                .totalRewardPoints(wallet.getBalance())
                .alreadyProcessed(false)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomOrderFileDTO getCustomOrderFile(UUID id, User currentUser) {
        CustomOrder order = customOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu in tùy chỉnh."));

        if (currentUser.getRole() != UserRole.ADMIN && !order.getBuyer().getId().equals(currentUser.getId())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Bạn không có quyền tải tệp thiết kế của đơn hàng này.");
        }

        String url = order.getAttachmentUrl();
        if (url == null || url.isBlank() || "custom-model".equalsIgnoreCase(url.trim())) {
            throw new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Đơn hàng này không có tệp 3D đính kèm.");
        }

        try {
            java.net.URL fileUrl = new java.net.URI(url).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) fileUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() >= 400) {
                throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Không thể tải tệp từ kho lưu trữ đám mây (HTTP " + conn.getResponseCode() + ").");
            }
            byte[] bytes = conn.getInputStream().readAllBytes();

            String ext = "stl";
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains(".obj")) ext = "obj";
            else if (lowerUrl.contains(".step")) ext = "step";
            else if (lowerUrl.contains(".stp")) ext = "stp";
            else if (lowerUrl.contains(".3mf")) ext = "3mf";
            else if (lowerUrl.contains(".glb")) ext = "glb";
            else if (lowerUrl.contains(".gltf")) ext = "gltf";
            else if (lowerUrl.contains(".png")) ext = "png";
            else if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) ext = "jpg";

            String modelName = order.getRulerModel() != null && !order.getRulerModel().isBlank()
                    ? order.getRulerModel().replaceAll("[^a-zA-Z0-9_-]", "_")
                    : "model";
            String filename = "PrintHub3D_" + modelName + "_" + id.toString().substring(0, 8) + "." + ext;
            String contentType = "application/octet-stream";
            if ("stl".equals(ext)) contentType = "model/stl";
            else if ("glb".equals(ext)) contentType = "model/gltf-binary";
            else if ("gltf".equals(ext)) contentType = "model/gltf+json";
            else if ("png".equals(ext)) contentType = "image/png";
            else if ("jpg".equals(ext)) contentType = "image/jpeg";

            return CustomOrderFileDTO.builder()
                    .data(bytes)
                    .filename(filename)
                    .contentType(contentType)
                    .build();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi tải stream tệp từ Cloudinary: {}", url, e);
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Lỗi khi xử lý tải tệp: " + e.getMessage());
        }
    }
}