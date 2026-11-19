package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.dto.order.*;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.OrderService;
import com.fpt.printhub_3d.service.OrderTransitionPolicy;
import com.fpt.printhub_3d.service.PaymentEvidenceService;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import lombok.RequiredArgsConstructor;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShippingInfoRepository shippingInfoRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final NotificationService notificationService;
    private final ReviewRepository reviewRepository;
    private final com.fpt.printhub_3d.service.InventoryService inventoryService;
    private final com.fpt.printhub_3d.service.OrderTransitionPolicy orderTransitionPolicy;
    private final SubscriptionBenefitService subscriptionBenefitService;

    @Autowired(required = false)
    private PaymentEvidenceService paymentEvidenceService;

    @Autowired(required = false)
    private OrderRewardService orderRewardService;

    @Autowired(required = false)
    private OrderRewardRecordRepository orderRewardRecordRepository;

    @Autowired(required = false)
    private Clock clock;

    @Autowired
    public OrderServiceImpl(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ShippingInfoRepository shippingInfoRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository,
            NotificationService notificationService,
            ReviewRepository reviewRepository,
            com.fpt.printhub_3d.service.InventoryService inventoryService,
            com.fpt.printhub_3d.service.OrderTransitionPolicy orderTransitionPolicy,
            @Autowired(required = false) SubscriptionBenefitService subscriptionBenefitService
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.shippingInfoRepository = shippingInfoRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.pointWalletRepository = pointWalletRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.notificationService = notificationService;
        this.reviewRepository = reviewRepository;
        this.inventoryService = inventoryService;
        this.orderTransitionPolicy = orderTransitionPolicy;
        this.subscriptionBenefitService = subscriptionBenefitService;
    }

    public OrderServiceImpl(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ShippingInfoRepository shippingInfoRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository,
            NotificationService notificationService,
            ReviewRepository reviewRepository,
            com.fpt.printhub_3d.service.InventoryService inventoryService,
            com.fpt.printhub_3d.service.OrderTransitionPolicy orderTransitionPolicy
    ) {
        this(
                productRepository,
                orderRepository,
                orderItemRepository,
                shippingInfoRepository,
                userRepository,
                paymentRepository,
                pointWalletRepository,
                pointTransactionRepository,
                notificationService,
                reviewRepository,
                inventoryService,
                orderTransitionPolicy,
                null
        );
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

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");
    private static final BigDecimal REWARD_POINT_UNIT = new BigDecimal("10000");
    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("30000");

    private static final Set<String> VALID_COLORS = Set.of("GREEN", "BLUE", "PINK", "WHITE", "BLACK");

    @Override
    @Transactional
    public List<OrderResponseDTO> createOrders(OrderCreateRequestDTO request, User buyer) {
        log.info("Bắt đầu xử lý tạo đơn hàng cho buyer: [{} - {}]", buyer.getId(), buyer.getFullName());

        List<OrderItemRequestDTO> items = request.items();
        if (items == null || items.isEmpty()) {
            throw new ApiException(OrderErrorCode.INVALID_ORDER_ITEMS, "Danh sách sản phẩm mua hàng không được để trống");
        }

        record ItemWithProduct(OrderItemRequestDTO item, Product product) {}
        List<ItemWithProduct> validatedItems = new ArrayList<>();
        Map<UUID, Integer> totalRequestedStock = new HashMap<>();

        for (OrderItemRequestDTO itemRequest : items) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ApiException(OrderErrorCode.PRODUCT_NOT_FOUND,
                            "Sản phẩm với ID " + itemRequest.productId() + " không tồn tại"));

            if (!"ACTIVE".equals(product.getStatus())) {
                throw new ApiException(OrderErrorCode.PRODUCT_NOT_FOUND,
                        "Sản phẩm '" + product.getTitle() + "' hiện không còn hoạt động hoặc đã ngừng bán");
            }

            User seller = product.getSeller();
            if (seller == null || !Boolean.TRUE.equals(seller.getIsActive())) {
                throw new ApiException(OrderErrorCode.SELLER_INACTIVE,
                        "Cửa hàng của sản phẩm '" + product.getTitle() + "' hiện không hoạt động");
            }

            if (itemRequest.color() != null && !itemRequest.color().trim().isEmpty()) {
                String colorUpper = itemRequest.color().trim().toUpperCase();
                if (!VALID_COLORS.contains(colorUpper)) {
                    throw new ApiException(CommonErrorCode.INVALID_INPUT,
                            "Màu sắc '" + itemRequest.color() + "' không hợp lệ. Chỉ chấp nhận: " + VALID_COLORS);
                }
            }

            totalRequestedStock.merge(product.getId(), itemRequest.quantity(), Integer::sum);
            validatedItems.add(new ItemWithProduct(itemRequest, product));
        }

        Map<UUID, List<ItemWithProduct>> itemsBySeller = validatedItems.stream()
                .collect(Collectors.groupingBy(iwp -> iwp.product().getSeller().getId()));

        List<OrderResponseDTO> createdOrders = new ArrayList<>();
        int sellerOrderIdx = 0;

        for (Map.Entry<UUID, List<ItemWithProduct>> sellerEntry : itemsBySeller.entrySet()) {
            List<ItemWithProduct> sellerItems = sellerEntry.getValue();
            User seller = sellerItems.getFirst().product().getSeller();

            BigDecimal itemsSubtotal = BigDecimal.ZERO;
            for (ItemWithProduct iwp : sellerItems) {
                BigDecimal itemTotal = iwp.product().getPrice().multiply(BigDecimal.valueOf(iwp.item().quantity()));
                itemsSubtotal = itemsSubtotal.add(itemTotal);
            }

            BigDecimal baseShippingFee = sellerOrderIdx == 0 ? DEFAULT_SHIPPING_FEE : BigDecimal.ZERO;
            sellerOrderIdx++;

            Order order = new Order();
            order.setBuyer(buyer);
            order.setSeller(seller);
            order.setSubtotalAmount(itemsSubtotal);
            order.setShippingFee(subscriptionBenefitService != null ? baseShippingFee : BigDecimal.ZERO);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setShippingDiscountAmount(BigDecimal.ZERO);
            order.setPriorityProduction(false);
            order.setTotalAmount(itemsSubtotal);
            order.setCommissionFee(itemsSubtotal.multiply(COMMISSION_RATE).setScale(0, RoundingMode.HALF_UP));
            order.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod().toUpperCase() : "PAYOS");
            order.setStatus("PENDING");
            order.setRewardProcessed(false);
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());
            Order savedOrder = orderRepository.save(order);

            if (subscriptionBenefitService != null) {
                SubscriptionBenefitService.AppliedBenefitSnapshot snap = subscriptionBenefitService.reserveAndApplyBenefits(
                        buyer,
                        SubscriptionBenefitService.UsageTargetType.ORDER,
                        savedOrder.getId(),
                        SubscriptionBenefitService.BenefitScope.MARKETPLACE,
                        itemsSubtotal,
                        baseShippingFee
                );
                savedOrder.setSubtotalAmount(snap.itemsSubtotal());
                savedOrder.setShippingFee(snap.baseShippingFee());
                savedOrder.setDiscountAmount(snap.discountAmount());
                savedOrder.setShippingDiscountAmount(snap.shippingDiscountAmount());
                savedOrder.setAppliedSubscriptionId(snap.appliedSubscriptionId());
                savedOrder.setAppliedSubscriptionName(snap.appliedSubscriptionName());
                savedOrder.setPriorityProduction(snap.priorityProduction());
                savedOrder.setTotalAmount(snap.finalTotal());
                BigDecimal netMerchandise = snap.itemsSubtotal().subtract(snap.discountAmount()).max(BigDecimal.ZERO);
                savedOrder.setCommissionFee(netMerchandise.multiply(COMMISSION_RATE).setScale(0, RoundingMode.HALF_UP));
                savedOrder = orderRepository.save(savedOrder);
            }

            BigDecimal totalAmount = savedOrder.getTotalAmount();

            ShippingInfo shippingInfo = new ShippingInfo();
            shippingInfo.setOrders(savedOrder);
            shippingInfo.setRecipientName(request.recipientName());
            shippingInfo.setPhone(request.phone());
            shippingInfo.setAddress(request.address());
            shippingInfo.setProvince(request.province());
            shippingInfo.setTrackingNumber(null);
            shippingInfoRepository.save(shippingInfo);

            List<OrderItemResponseDTO> itemResponses = new ArrayList<>();
            Map<UUID, Integer> sellerProductQuantities = new HashMap<>();
            for (ItemWithProduct iwp : sellerItems) {
                sellerProductQuantities.merge(iwp.product().getId(), iwp.item().quantity(), Integer::sum);
                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(savedOrder);
                orderItem.setProduct(iwp.product());
                orderItem.setQuantity(iwp.item().quantity());
                orderItem.setUnitPrice(iwp.product().getPrice());
                orderItem.setColor(iwp.item().color() != null ? iwp.item().color().trim().toUpperCase() : null);
                orderItem.setEngravingText(iwp.item().engravingText());

                OrderItem savedItem = orderItemRepository.save(orderItem);

                itemResponses.add(OrderItemResponseDTO.builder()
                        .id(savedItem.getId())
                        .productId(iwp.product().getId())
                        .productTitle(iwp.product().getTitle())
                        .quantity(savedItem.getQuantity())
                        .unitPrice(savedItem.getUnitPrice())
                        .subTotal(savedItem.getUnitPrice().multiply(BigDecimal.valueOf(savedItem.getQuantity())))
                        .color(savedItem.getColor())
                        .engravingText(savedItem.getEngravingText())
                        .build());
            }

            String paymentMethod = request.paymentMethod().trim().toUpperCase();
            String paymentStatus = "PENDING";
            String orderCode = null;

            if ("COD".equals(paymentMethod)) {
                Payment payment = new Payment();
                payment.setOrder(savedOrder);
                payment.setAmount(totalAmount);
                payment.setGateway("COD");
                payment.setStatus("PENDING");
                payment.setTransactionId(null);
                payment.setCreatedAt(Instant.now());
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
            }

            // Quản lý giữ chỗ tồn kho (ACTIVE) và lưu hạn thanh toán
            Instant expiresAt = "PAYOS".equals(paymentMethod) ? Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES) : null;
            inventoryService.reserveForOrder(savedOrder.getId(), sellerProductQuantities, "ORDER_" + savedOrder.getId(), expiresAt);
            if (expiresAt != null) {
                savedOrder.setPaymentDueAt(expiresAt);
                orderRepository.save(savedOrder);
            }

            OrderDisplayMetadata.RewardSnapshot createRewardSnap = OrderDisplayMetadata.computeRewardSnapshot(
                    savedOrder.getStatus(),
                    savedOrder.resolveZeroAmountSettledAt() != null,
                    OrderDisplayMetadata.resolveStandaloneOrderNetGoods(savedOrder),
                    null,
                    savedOrder.getRewardProcessed(),
                    savedOrder.getRewardPointsEarned(),
                    orderRewardService
            );

            createdOrders.add(OrderResponseDTO.builder()
                    .id(savedOrder.getId())
                    .buyerId(buyer.getId())
                    .buyerName(buyer.getFullName())
                    .sellerId(seller.getId())
                    .sellerName(seller.getFullName())
                    .totalAmount(savedOrder.getTotalAmount())
                    .commissionFee(savedOrder.getCommissionFee())
                    .status(savedOrder.getStatus())
                    .paymentMethod(paymentMethod)
                    .paymentStatus(paymentStatus)
                    .orderCode(orderCode)
                    .shippingInfo(ShippingInfoResponseDTO.builder()
                            .recipientName(shippingInfo.getRecipientName())
                            .phone(shippingInfo.getPhone())
                            .address(shippingInfo.getAddress())
                            .province(shippingInfo.getProvince())
                            .trackingNumber(shippingInfo.getTrackingNumber())
                            .build())
                    .items(itemResponses)
                    .createdAt(savedOrder.getCreatedAt())
                    .updatedAt(savedOrder.getUpdatedAt())
                    .paymentDueAt(savedOrder.getPaymentDueAt())
                    .subtotalAmount(savedOrder.getSubtotalAmount())
                    .shippingFee(savedOrder.getShippingFee())
                    .discountAmount(savedOrder.getDiscountAmount())
                    .shippingDiscountAmount(savedOrder.getShippingDiscountAmount())
                    .appliedSubscriptionId(savedOrder.getAppliedSubscriptionId())
                    .appliedSubscriptionName(savedOrder.getAppliedSubscriptionName())
                    .priorityProduction(savedOrder.getPriorityProduction())
                    .estimatedRewardPoints(createRewardSnap.estimatedRewardPoints())
                    .rewardStatus(createRewardSnap.rewardStatus())
                    .build());
        }

        return createdOrders;
    }

    @Override
    @Transactional
    public RewardCompletionResponseDTO completeRewards(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng ID: " + orderId));

        if (!"COMPLETED".equals(order.getStatus())) {
            throw new ApiException(OrderErrorCode.ORDER_NOT_COMPLETED, "Đơn hàng chưa ở trạng thái COMPLETED");
        }

        if (orderRewardService != null) {
            PaymentEvidenceService.SourceType targetType = order.getCheckoutOrder() != null
                    ? PaymentEvidenceService.SourceType.CHECKOUT_ORDER
                    : PaymentEvidenceService.SourceType.ORDER;
            UUID targetId = order.getCheckoutOrder() != null
                    ? order.getCheckoutOrder().getId()
                    : orderId;
            var reward = orderRewardService.processCompletionReward(targetType, targetId);
            if (reward.status() == OrderRewardStatus.PENDING_PAYMENT_EVIDENCE) {
                throw new ApiException(OrderErrorCode.PAYMENT_REQUIRED, "Đơn hàng chưa hoàn tất thanh toán hợp lệ để nhận điểm thưởng");
            }
            return reward.toLegacyResponse().withOrderId(orderId);
        }

        if (order.resolveZeroAmountSettledAt() != null) {
            User buyer = order.getBuyer();
            return RewardCompletionResponseDTO.forZeroAmountPromo(
                    order.getId(), buyer.getId(), buyer.getFullName(), buyer.getRewardPoints());
        }

        Payment payment = findOrderPayment(order);
        if (payment == null || !"SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            throw new ApiException(OrderErrorCode.PAYMENT_REQUIRED, "Đơn hàng chưa hoàn tất thanh toán hợp lệ để nhận điểm thưởng");
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

        BigDecimal baseMerch = order.getSubtotalAmount() != null ? order.getSubtotalAmount()
                : (order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal eligibleNet = baseMerch.subtract(discount).max(BigDecimal.ZERO);
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
                    .description("Hoàn thành đơn #" + order.getId())
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        order.setRewardProcessed(true);
        order.setRewardPointsEarned(earnedPoints);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        return RewardCompletionResponseDTO.builder()
                .orderId(order.getId())
                .customerId(buyer.getId())
                .customerName(buyer.getFullName())
                .rewardPointsEarned(earnedPoints)
                .totalRewardPoints(wallet.getBalance())
                .alreadyProcessed(false)
                .build();
    }

    private OrderResponseDTO mapOrderToDTO(Order order, ShippingInfo shippingInfo, List<OrderItem> items) {
        List<Review> reviews = reviewRepository.findByOrderId(order.getId());
        OrderRewardRecord rewardRecord = resolveSingleOrderRewardRecord(order);
        return mapOrderToDTO(order, shippingInfo, items, reviews, rewardRecord);
    }

    private OrderResponseDTO mapOrderToDTO(
            Order order,
            ShippingInfo shippingInfo,
            List<OrderItem> items,
            List<Review> orderReviews,
            OrderRewardRecord rewardRecord
    ) {
        Payment payment = findOrderPayment(order);

        String pMethod = order.getPaymentMethod() != null ? order.getPaymentMethod()
                : (order.getCheckoutOrder() != null && order.getCheckoutOrder().getPaymentMethod() != null
                        ? order.getCheckoutOrder().getPaymentMethod()
                        : (payment != null ? payment.getGateway() : "PAYOS"));
        String pStatus = order.resolveZeroAmountSettledAt() != null ? "SUCCESS"
                : payment != null ? payment.getStatus() : ("COD".equalsIgnoreCase(pMethod) ? "UNPAID" : "PENDING");

        User current = getCurrentUserSafely();
        boolean isAdmin = current != null && current.getRole() == UserRole.ADMIN;
        boolean isOwner = current != null && order.getBuyer() != null && current.getId().equals(order.getBuyer().getId());
        if (current == null) {
            isAdmin = true;
        }

        OrderTransitionPolicy.TransitionEvaluation eval = orderTransitionPolicy.evaluateActions(
                OrderTransitionPolicy.OrderType.MARKETPLACE,
                order.getStatus(),
                pMethod,
                pStatus,
                isAdmin,
                isOwner
        );

        ShippingInfoResponseDTO shippingDTO = null;
        if (shippingInfo != null) {
            shippingDTO = ShippingInfoResponseDTO.builder()
                    .recipientName(shippingInfo.getRecipientName())
                    .phone(shippingInfo.getPhone())
                    .address(shippingInfo.getAddress())
                    .province(shippingInfo.getProvince())
                    .trackingNumber(shippingInfo.getTrackingNumber())
                    .build();
        }

        Instant now = (clock != null ? clock : Clock.systemUTC()).instant();
        List<Review> reviews = orderReviews != null ? orderReviews : List.of();
        Map<Long, Review> reviewByItem = reviews.stream()
                .filter(r -> r.getOrderItem() != null)
                .collect(Collectors.toMap(r -> r.getOrderItem().getId(), r -> r, (r1, r2) -> r1));

        List<OrderItemResponseDTO> itemDTOs = items != null ? items.stream()
                .map(item -> {
                    Review itemReview = reviewByItem.get(item.getId());
                    OrderDisplayMetadata.ItemReviewMeta reviewMeta =
                            OrderDisplayMetadata.computeItemReviewMeta(order, itemReview, isOwner, now);
                    return OrderItemResponseDTO.builder()
                            .id(item.getId())
                            .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                            .productTitle(item.getProduct() != null ? item.getProduct().getTitle() : "Sản phẩm")
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                            .subTotal(item.getUnitPrice() != null ? item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())) : BigDecimal.ZERO)
                            .color(item.getColor())
                            .engravingText(item.getEngravingText())
                            .reviewId(reviewMeta.reviewId())
                            .reviewRating(reviewMeta.reviewRating())
                            .canReview(reviewMeta.canReview())
                            .canEditReview(reviewMeta.canEditReview())
                            .reviewComment(reviewMeta.reviewComment())
                            .reviewCreatedAt(reviewMeta.reviewCreatedAt())
                            .build();
                })
                .toList() : List.of();

        OrderDisplayMetadata.RewardSnapshot rewardSnap = OrderDisplayMetadata.computeRewardSnapshot(
                order.getStatus(),
                order.resolveZeroAmountSettledAt() != null,
                OrderDisplayMetadata.resolveStandaloneOrderNetGoods(order),
                rewardRecord,
                order.getRewardProcessed(),
                order.getRewardPointsEarned(),
                orderRewardService
        );

        return OrderResponseDTO.builder()
                .id(order.getId())
                .buyerId(order.getBuyer() != null ? order.getBuyer().getId() : null)
                .buyerName(order.getBuyer() != null ? order.getBuyer().getFullName() : null)
                .sellerId(order.getSeller() != null ? order.getSeller().getId() : null)
                .sellerName(order.getSeller() != null ? order.getSeller().getFullName() : null)
                .totalAmount(order.getTotalAmount())
                .commissionFee(order.getCommissionFee())
                .status(order.getStatus())
                .paymentMethod(pMethod)
                .paymentStatus(pStatus)
                .orderCode(payment != null ? payment.getTransactionId() : null)
                .shippingInfo(shippingDTO)
                .items(itemDTOs)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .paymentDueAt(order.getPaymentDueAt())
                .allowedActions(eval.allowedActions())
                .blockedActions(eval.blockedActions())
                .subtotalAmount(order.getSubtotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .shippingDiscountAmount(order.getShippingDiscountAmount())
                .appliedSubscriptionId(order.getAppliedSubscriptionId())
                .appliedSubscriptionName(order.getAppliedSubscriptionName())
                .priorityProduction(order.getPriorityProduction())
                .estimatedRewardPoints(rewardSnap.estimatedRewardPoints())
                .rewardStatus(rewardSnap.rewardStatus())
                .build();
    }

    private OrderRewardRecord resolveSingleOrderRewardRecord(Order order) {
        if (orderRewardRecordRepository == null || order == null) {
            return null;
        }
        String srcType = order.getCheckoutOrder() != null ? "CHECKOUT_ORDER" : "ORDER";
        UUID srcId = order.getCheckoutOrder() != null ? order.getCheckoutOrder().getId() : order.getId();
        return orderRewardRecordRepository.findBySourceTypeAndSourceIdAndRewardType(
                srcType, srcId, OrderRewardRecord.REWARD_TYPE_COMPLETION).orElse(null);
    }

    private List<OrderResponseDTO> mapOrdersBatch(List<Order> orders) {
        if (orders.isEmpty()) return List.of();
        List<UUID> orderIds = orders.stream().map(Order::getId).toList();
        Map<UUID, ShippingInfo> shippingMap = shippingInfoRepository.findByIdIn(orderIds).stream()
                .collect(Collectors.toMap(ShippingInfo::getId, s -> s));
        Map<UUID, List<OrderItem>> itemsMap = orderItemRepository.findByOrderIn(orders).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
        Map<UUID, List<Review>> reviewsMap = reviewRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(r -> r.getOrder().getId()));

        Map<UUID, OrderRewardRecord> standaloneRewards = new HashMap<>();
        Map<UUID, OrderRewardRecord> parentRewards = new HashMap<>();
        if (orderRewardRecordRepository != null) {
            List<UUID> standaloneIds = orders.stream()
                    .filter(o -> o.getCheckoutOrder() == null).map(Order::getId).toList();
            if (!standaloneIds.isEmpty()) {
                orderRewardRecordRepository.findBySourceTypeAndSourceIdInAndRewardType(
                        "ORDER", standaloneIds, OrderRewardRecord.REWARD_TYPE_COMPLETION)
                        .forEach(r -> standaloneRewards.put(r.getSourceId(), r));
            }
            List<UUID> checkoutIds = orders.stream()
                    .filter(o -> o.getCheckoutOrder() != null)
                    .map(o -> o.getCheckoutOrder().getId()).distinct().toList();
            if (!checkoutIds.isEmpty()) {
                orderRewardRecordRepository.findBySourceTypeAndSourceIdInAndRewardType(
                        "CHECKOUT_ORDER", checkoutIds, OrderRewardRecord.REWARD_TYPE_COMPLETION)
                        .forEach(r -> parentRewards.put(r.getSourceId(), r));
            }
        }

        return orders.stream().map(o -> {
            OrderRewardRecord rec = o.getCheckoutOrder() != null
                    ? parentRewards.get(o.getCheckoutOrder().getId())
                    : standaloneRewards.get(o.getId());
            return mapOrderToDTO(o, shippingMap.get(o.getId()),
                    itemsMap.getOrDefault(o.getId(), List.of()),
                    reviewsMap.getOrDefault(o.getId(), List.of()),
                    rec);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getMyOrders(UUID buyerId) {
        return mapOrdersBatch(orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderById(UUID id, User currentUser) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (currentUser.getRole() != UserRole.ADMIN && !order.getBuyer().getId().equals(currentUser.getId())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Bạn không có quyền truy cập đơn hàng này");
        }

        ShippingInfo shippingInfo = shippingInfoRepository.findById(id).orElse(null);
        List<OrderItem> items = orderItemRepository.findByOrderId(id);

        return mapOrderToDTO(order, shippingInfo, items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrders() {
        List<Order> orders = orderRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        return mapOrdersBatch(orders);
    }

    @Override
    @Transactional
    public void updateOrderStatus(UUID id, String nextStatus, User currentUser) {
        updateOrderStatus(id, nextStatus, currentUser, null);
    }

    @Override
    @Transactional
    public void updateOrderStatus(UUID id, String nextStatus, User currentUser, BigDecimal codCollectedAmount) {
        Order order = orderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        String currentStatus = order.getStatus();
        Payment payment = findOrderPayment(order);

        String paymentMethod = order.getPaymentMethod() != null ? order.getPaymentMethod()
                : (order.getCheckoutOrder() != null && order.getCheckoutOrder().getPaymentMethod() != null
                        ? order.getCheckoutOrder().getPaymentMethod()
                        : (payment != null ? payment.getGateway() : "PAYOS"));
        boolean zeroAmountSettlement = order.resolveZeroAmountSettledAt() != null;
        String paymentStatus = zeroAmountSettlement ? "SUCCESS" : payment != null ? payment.getStatus()
                : ("COD".equalsIgnoreCase(paymentMethod) ? "UNPAID" : "PENDING");

        boolean cod = "COD".equalsIgnoreCase(paymentMethod);
        if (codCollectedAmount != null) {
            if (!cod || order.getCheckoutOrder() != null
                    || !"COMPLETED".equals(orderTransitionPolicy.normalizeTargetStatus(nextStatus))) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT,
                        "Xác nhận tiền COD khi hoàn tất đúng cấp đơn thu tiền.");
            }
            if ("COMPLETED".equals(currentStatus)) {
                if (orderRewardService != null) {
                    orderRewardService.completeCodAndReward(PaymentEvidenceService.SourceType.ORDER, id,
                            codCollectedAmount, currentUser);
                } else {
                    if (paymentEvidenceService != null) {
                        paymentEvidenceService.recordCodCollection(PaymentEvidenceService.SourceType.ORDER, id,
                                codCollectedAmount, currentUser);
                    }
                    completeRewards(id);
                }
                return;
            }
        }

        String resolvedStatus = orderTransitionPolicy.validateAndResolveNextStatus(
                OrderTransitionPolicy.OrderType.MARKETPLACE,
                currentStatus,
                nextStatus,
                paymentMethod,
                paymentStatus,
                currentUser,
                order.getBuyer().getId()
        );

        if ("CANCELLED".equals(resolvedStatus)) {
            if (payment != null && Payment.PENDING_RECONCILIATION.equals(payment.getStatus())) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Giao dịch đang chờ đối soát.");
            }
            if (payment != null && "PAYOS".equalsIgnoreCase(payment.getGateway()) && payment.getTransactionId() != null) {
                payment.setStatus("CANCELLED");
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
            }
            // Hoàn lại tồn kho cho đơn hủy PENDING
            inventoryService.releaseActiveReservations(order.getId(), "Hủy đơn hàng PENDING", currentUser.getId().toString());
            if (subscriptionBenefitService != null) {
                subscriptionBenefitService.releaseReservedBenefits(
                        SubscriptionBenefitService.UsageTargetType.ORDER, order.getId());
            }
        } else {
            if ("PREPARING".equals(resolvedStatus) || "PRINTING".equals(resolvedStatus)) {
                inventoryService.confirmReservation(order.getId());
            }
        }

        Instant transitionAt = Instant.now();
        order.setStatus(resolvedStatus);
        order.setUpdatedAt(transitionAt);
        if ("COMPLETED".equals(resolvedStatus) && order.getCompletedAt() == null) {
            order.setCompletedAt(transitionAt);
        }
        orderRepository.save(order);

        if ("COMPLETED".equals(resolvedStatus)) {
            if (orderRewardService != null) {
                if (codCollectedAmount != null) {
                    orderRewardService.completeCodAndReward(PaymentEvidenceService.SourceType.ORDER, id,
                            codCollectedAmount, currentUser);
                } else {
                    orderRewardService.processCompletionReward(PaymentEvidenceService.SourceType.ORDER, id);
                }
            } else {
                if (codCollectedAmount != null && paymentEvidenceService != null) {
                    paymentEvidenceService.recordCodCollection(PaymentEvidenceService.SourceType.ORDER, id,
                            codCollectedAmount, currentUser);
                }
                if (!cod || zeroAmountSettlement || codCollectedAmount != null || "SUCCESS".equals(paymentStatus)) {
                    completeRewards(id);
                }
            }
        }

        notificationService.sendNotification(order.getBuyer(),
                "Cập nhật đơn hàng",
                "Đơn hàng " + id + " đã chuyển sang trạng thái: " + resolvedStatus,
                "ORDER",
                "/orders");
    }

    private Payment findOrderPayment(Order order) {
        return order.getCheckoutOrder() != null
                ? paymentRepository.findByCheckoutOrderId(order.getCheckoutOrder().getId()).orElse(null)
                : paymentRepository.findByOrderId(order.getId()).orElse(null);
    }
}
