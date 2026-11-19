package com.fpt.printhub_3d.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CheckoutOrderErrorCode;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.dto.checkout.AdminCheckoutOrderStatusRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderBatchQuoteRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderDetailResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import com.fpt.printhub_3d.dto.order.BlockedActionDTO;
import com.fpt.printhub_3d.dto.order.OrderItemResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO;
import com.fpt.printhub_3d.dto.payment.PaymentEvidenceResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.CheckoutOrderService;
import com.fpt.printhub_3d.service.CloudinaryService;
import com.fpt.printhub_3d.service.InventoryService;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.service.OrderTransitionPolicy;
import com.fpt.printhub_3d.service.PaymentService;
import com.fpt.printhub_3d.service.PaymentEvidenceService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService.BenefitScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class CheckoutOrderServiceImpl implements CheckoutOrderService {

    private final CheckoutOrderRepository checkoutOrderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomOrderRepository customOrderRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final OrderTransitionPolicy orderTransitionPolicy;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final CloudinaryService cloudinaryService;
    private final StoreSettingRepository storeSettingRepository;
    private final SubscriptionBenefitService subscriptionBenefitService;
    private final OrderEmailEvents emailEvents;

    @Autowired
    private PaymentEvidenceService paymentEvidenceService;

    @Autowired
    private OrderRewardService orderRewardService;

    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("30000");

    @Autowired
    public CheckoutOrderServiceImpl(
            CheckoutOrderRepository checkoutOrderRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CustomOrderRepository customOrderRepository,
            PaymentService paymentService,
            InventoryService inventoryService,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            OrderTransitionPolicy orderTransitionPolicy,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            CloudinaryService cloudinaryService,
            StoreSettingRepository storeSettingRepository,
            @Autowired(required = false) SubscriptionBenefitService subscriptionBenefitService,
            OrderEmailEvents emailEvents
    ) {
        this.checkoutOrderRepository = checkoutOrderRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customOrderRepository = customOrderRepository;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.orderTransitionPolicy = orderTransitionPolicy != null ? orderTransitionPolicy : new OrderTransitionPolicy();
        this.notificationService = notificationService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.cloudinaryService = cloudinaryService;
        this.storeSettingRepository = storeSettingRepository;
        this.subscriptionBenefitService = subscriptionBenefitService;
        this.emailEvents = emailEvents;
    }

    public CheckoutOrderServiceImpl(
            CheckoutOrderRepository checkoutOrderRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CustomOrderRepository customOrderRepository,
            PaymentService paymentService,
            InventoryService inventoryService,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            OrderTransitionPolicy orderTransitionPolicy,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            CloudinaryService cloudinaryService,
            StoreSettingRepository storeSettingRepository
    ) {
        this(
                checkoutOrderRepository,
                cartRepository,
                cartItemRepository,
                productRepository,
                orderRepository,
                orderItemRepository,
                customOrderRepository,
                paymentService,
                inventoryService,
                userRepository,
                paymentRepository,
                orderTransitionPolicy,
                notificationService,
                objectMapper,
                cloudinaryService,
                storeSettingRepository,
                null,
                null
        );
    }

    public CheckoutOrderServiceImpl(
            CheckoutOrderRepository checkoutOrderRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CustomOrderRepository customOrderRepository,
            PaymentService paymentService,
            InventoryService inventoryService,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            OrderTransitionPolicy orderTransitionPolicy,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            CloudinaryService cloudinaryService
    ) {
        this(
                checkoutOrderRepository,
                cartRepository,
                cartItemRepository,
                productRepository,
                orderRepository,
                orderItemRepository,
                customOrderRepository,
                paymentService,
                inventoryService,
                userRepository,
                paymentRepository,
                orderTransitionPolicy,
                notificationService,
                objectMapper,
                cloudinaryService,
                null
        );
    }

    public CheckoutOrderServiceImpl(
            CheckoutOrderRepository checkoutOrderRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CustomOrderRepository customOrderRepository,
            PaymentService paymentService,
            InventoryService inventoryService,
            UserRepository userRepository
    ) {
        this(
                checkoutOrderRepository,
                cartRepository,
                cartItemRepository,
                productRepository,
                orderRepository,
                orderItemRepository,
                customOrderRepository,
                paymentService,
                inventoryService,
                userRepository,
                null,
                new OrderTransitionPolicy(),
                null,
                new ObjectMapper(),
                null
        );
    }

    private BigDecimal resolveDefaultShippingFee() {
        if (storeSettingRepository != null) {
            try {
                var feeOpt = storeSettingRepository.findById("shippingFee");
                if (feeOpt.isPresent() && feeOpt.get().getValue() != null) {
                    BigDecimal configured = new BigDecimal(feeOpt.get().getValue().trim());
                    if (configured.signum() >= 0) {
                        return configured;
                    }
                }
            } catch (Exception ignored) {}
        }
        return DEFAULT_SHIPPING_FEE;
    }

    private BigDecimal resolveCustomLineTotal(CustomOrder c) {
        return c != null ? OrderPricing.quotedLineTotal(c) : BigDecimal.ZERO;
    }

    @Override
    public CheckoutOrderResponseDTO createCheckoutOrder(User user, CheckoutOrderCreateRequestDTO request) {
        log.info("Tạo CheckoutOrder cho user: {}", user.getId());

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<CheckoutOrder> existing = checkoutOrderRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                log.info("Request idempotent: Đơn hàng với idempotencyKey [{}] đã tồn tại, trả lại đơn cũ", request.idempotencyKey());
                return toDTO(existing.get(), null);
            }
        }

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.EMPTY_SELECTION));

        List<CartItem> allItems = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        Set<String> selectedIdSet = new HashSet<>(request.selectedLineIds());

        List<CartItem> selectedItems = allItems.stream()
                .filter(item -> (item.getClientLineId() != null && selectedIdSet.contains(item.getClientLineId().toString()))
                        || selectedIdSet.contains(String.valueOf(item.getId())))
                .toList();

        if (selectedItems.isEmpty()) {
            throw new ApiException(CheckoutOrderErrorCode.EMPTY_SELECTION);
        }

        List<CartItem> customLines = selectedItems.stream()
                .filter(i -> i.getCustomDesign() != null && !i.getCustomDesign().isBlank())
                .toList();
        List<CartItem> marketplaceLines = selectedItems.stream()
                .filter(i -> i.getProduct() != null && (i.getCustomDesign() == null || i.getCustomDesign().isBlank()))
                .toList();

        String shippingMode = (request.shippingMode() != null && !request.shippingMode().isBlank())
                ? request.shippingMode().toUpperCase()
                : "COMMON";

        if ("COMMON".equals(shippingMode) && !marketplaceLines.isEmpty()) {
            Set<UUID> sellerIds = marketplaceLines.stream()
                    .map(i -> i.getProduct().getSeller() != null ? i.getProduct().getSeller().getId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (sellerIds.size() > 1) {
                throw new ApiException(CheckoutOrderErrorCode.SELLER_MISMATCH_FOR_COMMON_SHIPPING);
            }
        }

        String normalizedPaymentMethod = (request.paymentMethod() != null && !request.paymentMethod().isBlank())
                ? request.paymentMethod().trim().toUpperCase()
                : "PAYOS";
        if (!customLines.isEmpty() && !"PAYOS".equalsIgnoreCase(normalizedPaymentMethod)) {
            throw new ApiException(CheckoutOrderErrorCode.COD_NOT_ALLOWED_FOR_CUSTOM,
                    "Đơn in theo yêu cầu chỉ hỗ trợ thanh toán trực tuyến PayOS");
        }
        String resolvedOrderType = (!marketplaceLines.isEmpty() && !customLines.isEmpty())
                ? "MIXED"
                : (!customLines.isEmpty() ? "CUSTOM" : "MARKETPLACE");

        CheckoutOrder checkoutOrder = new CheckoutOrder();
        checkoutOrder.setUser(user);
        checkoutOrder.setOrderType(resolvedOrderType);
        checkoutOrder.setRecipientName(request.recipientName() != null && !request.recipientName().isBlank()
                ? request.recipientName().trim() : user.getFullName());
        checkoutOrder.setRecipientPhone(request.recipientPhone() != null && !request.recipientPhone().isBlank()
                ? request.recipientPhone().trim() : user.getPhone());
        checkoutOrder.setShippingAddress(request.shippingAddress());
        checkoutOrder.setShippingMode(shippingMode);
        checkoutOrder.setStatus(customLines.isEmpty() ? "CONFIRMED" : "PENDING_QUOTE");
        BigDecimal baseShipFee = customLines.isEmpty() ? resolveDefaultShippingFee() : BigDecimal.ZERO;
        checkoutOrder.setShippingFee(baseShipFee);
        checkoutOrder.setDiscountAmount(BigDecimal.ZERO);
        checkoutOrder.setShippingDiscountAmount(BigDecimal.ZERO);
        checkoutOrder.setPriorityProduction(false);
        checkoutOrder.setPaymentMethod(normalizedPaymentMethod);
        checkoutOrder.setSummaryVersion(1L);
        checkoutOrder.setIdempotencyKey(request.idempotencyKey() != null ? request.idempotencyKey().trim() : null);
        checkoutOrder.setCreatedAt(Instant.now());
        checkoutOrder.setUpdatedAt(Instant.now());

        BigDecimal marketSubtotal = marketplaceLines.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        checkoutOrder.setItemsSubtotal(marketSubtotal);
        checkoutOrder.setTotalAmount(marketSubtotal.add(baseShipFee));

        if (!customLines.isEmpty()) {
            checkoutOrder.setStatus("PENDING_QUOTE");
        } else {
            checkoutOrder.setStatus("CONFIRMED");
        }

        checkoutOrder = checkoutOrderRepository.save(checkoutOrder);

        if (customLines.isEmpty() && subscriptionBenefitService != null) {
            SubscriptionBenefitService.AppliedBenefitSnapshot snap = subscriptionBenefitService.reserveAndApplyBenefits(
                    user,
                    SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER,
                    checkoutOrder.getId(),
                    SubscriptionBenefitService.BenefitScope.MARKETPLACE,
                    marketSubtotal,
                    baseShipFee
            );
            checkoutOrder.setItemsSubtotal(snap.itemsSubtotal());
            checkoutOrder.setShippingFee(snap.baseShippingFee());
            checkoutOrder.setDiscountAmount(snap.discountAmount());
            checkoutOrder.setShippingDiscountAmount(snap.shippingDiscountAmount());
            checkoutOrder.setAppliedSubscriptionId(snap.appliedSubscriptionId());
            checkoutOrder.setAppliedSubscriptionName(snap.appliedSubscriptionName());
            checkoutOrder.setPriorityProduction(snap.priorityProduction());
            checkoutOrder.setTotalAmount(snap.finalTotal());
            checkoutOrder.setUpdatedAt(Instant.now());
            if (snap.fullyDiscounted()) {
                settleZeroAmountCheckout(checkoutOrder, checkoutOrder.getUpdatedAt());
            }
            checkoutOrder = checkoutOrderRepository.save(checkoutOrder);
        }

        // Tạo CustomOrder cho các dòng thiết kế custom
        for (CartItem cItem : customLines) {
            CustomOrder customOrder = createChildCustomOrder(user, checkoutOrder, request, cItem);
            customOrderRepository.save(customOrder);
        }

        if (!customLines.isEmpty() && notificationService != null) {
            notifyAdminsNewCustomCheckout(checkoutOrder, user, customLines.size());
        }

        boolean zeroAmountSettlement = checkoutOrder.getZeroAmountSettledAt() != null;
        // Tạo Order cho các dòng sản phẩm Marketplace
        if (!marketplaceLines.isEmpty()) {
            User fallbackSeller = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == com.fpt.printhub_3d.entity.Enumeration.UserRole.ADMIN)
                    .findFirst()
                    .orElse(user);

            Map<UUID, List<CartItem>> itemsBySeller = marketplaceLines.stream()
                    .collect(Collectors.groupingBy(i -> {
                        if (i.getProduct() != null && i.getProduct().getSeller() != null && i.getProduct().getSeller().getId() != null) {
                            return i.getProduct().getSeller().getId();
                        }
                        return fallbackSeller.getId();
                    }));

            for (Map.Entry<UUID, List<CartItem>> entry : itemsBySeller.entrySet()) {
                CartItem firstItem = entry.getValue().getFirst();
                User seller = (firstItem.getProduct() != null && firstItem.getProduct().getSeller() != null)
                        ? firstItem.getProduct().getSeller()
                        : fallbackSeller;
                BigDecimal sellerTotal = entry.getValue().stream()
                        .map(i -> (i.getProduct() != null && i.getProduct().getPrice() != null ? i.getProduct().getPrice() : BigDecimal.ZERO)
                                .multiply(BigDecimal.valueOf(i.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                Order order = new Order();
                order.setBuyer(user);
                order.setSeller(seller);
                order.setCheckoutOrder(checkoutOrder);
                order.setPaymentMethod(checkoutOrder.getPaymentMethod());
                order.setStatus(zeroAmountSettlement ? "PAID" : "PENDING");
                order.setSubtotalAmount(sellerTotal);
                order.setTotalAmount(zeroAmountSettlement ? BigDecimal.ZERO : sellerTotal);
                if (zeroAmountSettlement) {
                    order.setDiscountAmount(sellerTotal);
                    order.setAppliedSubscriptionId(checkoutOrder.getAppliedSubscriptionId());
                    order.setAppliedSubscriptionName(checkoutOrder.getAppliedSubscriptionName());
                }
                order.setPriorityProduction(checkoutOrder.getPriorityProduction());
                order.setCommissionFee(BigDecimal.ZERO);
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(Instant.now());
                order = orderRepository.save(order);

                for (CartItem ci : entry.getValue()) {
                    OrderItem orderItem = new OrderItem();
                    orderItem.setOrder(order);
                    orderItem.setProduct(ci.getProduct());
                    orderItem.setQuantity(ci.getQuantity());
                    orderItem.setUnitPrice(ci.getProduct().getPrice());
                    orderItemRepository.save(orderItem);
                }

                // Giữ chỗ tồn kho cho đơn marketplace (ACTIVE)
                Map<UUID, Integer> sellerProductQuantities = new HashMap<>();
                for (CartItem ci : entry.getValue()) {
                    sellerProductQuantities.merge(ci.getProduct().getId(), ci.getQuantity(), Integer::sum);
                }
                Instant expiresAt = !zeroAmountSettlement && "PAYOS".equalsIgnoreCase(checkoutOrder.getPaymentMethod())
                        ? Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES) : null;
                inventoryService.reserveForOrder(order.getId(), sellerProductQuantities, "CHECKOUT_ORDER_" + checkoutOrder.getId(), expiresAt);
                if (zeroAmountSettlement) {
                    inventoryService.confirmReservation(order.getId());
                } else if (expiresAt != null) {
                    order.setPaymentDueAt(expiresAt);
                    orderRepository.save(order);
                }
            }
        }

        // Xóa các dòng đã chọn khỏi giỏ hàng
        cartItemRepository.deleteAll(selectedItems);
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        if ("CONFIRMED".equals(checkoutOrder.getStatus()) && "PAYOS".equalsIgnoreCase(checkoutOrder.getPaymentMethod())) {
            prepareCheckoutPayment(checkoutOrder);
        }

        return toDTO(checkoutOrder, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutOrderResponseDTO getCheckoutOrder(User user, UUID id) {
        CheckoutOrder order = checkoutOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(user.getId()) && user.getRole() != com.fpt.printhub_3d.entity.Enumeration.UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return toDTO(order, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutOrderResponseDTO> getMyCheckoutOrders(User user) {
        List<CheckoutOrder> orders = checkoutOrderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return orders.stream().map(o -> toDTO(o, null)).toList();
    }

    @Override
    public CheckoutOrderResponseDTO confirmCheckoutOrder(User user, UUID id, CheckoutOrderConfirmRequestDTO request) {
        CheckoutOrder checkoutOrder = checkoutOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));

        if (!checkoutOrder.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        String status = checkoutOrder.getStatus();
        if ("CANCELLED".equals(status) || "EXPIRED".equals(status)) {
            throw new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND);
        }

        Long version = checkoutOrder.getSummaryVersion();
        if (!Objects.equals(version, request.summaryVersion())) {
            throw checkoutConflict(checkoutOrder, CheckoutOrderErrorCode.CONFIRMATION_VERSION_CONFLICT,
                    toDTO(checkoutOrder, null).pricingBreakdown());
        }

        String storedMethod = checkoutOrder.getPaymentMethod() != null ? checkoutOrder.getPaymentMethod() : "PAYOS";
        String paymentMethod = request.paymentMethod() != null && !request.paymentMethod().isBlank()
                ? request.paymentMethod().trim().toUpperCase(Locale.ROOT) : storedMethod.toUpperCase(Locale.ROOT);
        if (!"PAYOS".equals(paymentMethod) && !"COD".equals(paymentMethod)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Phương thức thanh toán không hợp lệ");
        }

        if ("CONFIRMED".equals(status) || "PROCESSING".equals(status) || "PAID".equals(status)) {
            if (!paymentMethod.equalsIgnoreCase(storedMethod)) {
                throw new ApiException(CheckoutOrderErrorCode.INVALID_PAYMENT_STATE);
            }
            BigDecimal expected = request.expectedTotalPayable();
            if (expected != null && (checkoutOrder.getTotalAmount() == null
                    || expected.compareTo(checkoutOrder.getTotalAmount()) != 0)) {
                throw checkoutConflict(checkoutOrder, CheckoutOrderErrorCode.CONFIRMATION_PRICE_CHANGED,
                        toDTO(checkoutOrder, null).pricingBreakdown());
            }
            if ("CONFIRMED".equals(status) && "COD".equals(paymentMethod)) {
                checkoutOrder.setStatus("PROCESSING");
                checkoutOrder.setUpdatedAt(Instant.now());
                checkoutOrderRepository.save(checkoutOrder);
            }
            return toDTO(checkoutOrder, null);
        }
        if (!"PENDING_QUOTE".equals(status) && !"QUOTED".equals(status)) {
            throw new ApiException(CheckoutOrderErrorCode.INVALID_PAYMENT_STATE);
        }

        List<CustomOrder> customList = customOrderRepository.findByCheckoutOrderId(id);
        List<CustomOrder> activeCustoms = customList.stream()
                .filter(c -> !"CANCELLED".equalsIgnoreCase(c.getStatus()))
                .toList();
        if ((!customList.isEmpty() && activeCustoms.isEmpty())
                || activeCustoms.stream().anyMatch(c -> !"QUOTED".equals(c.getStatus())
                        || resolveCustomLineTotal(c).signum() <= 0)) {
            throw new ApiException(CheckoutOrderErrorCode.CUSTOM_NOT_ALL_QUOTED);
        }
        if (!activeCustoms.isEmpty() && !"PAYOS".equals(paymentMethod)) {
            throw new ApiException(CheckoutOrderErrorCode.COD_NOT_ALLOWED_FOR_CUSTOM);
        }

        List<Order> marketOrders = orderRepository.findByCheckoutOrderId(id).stream()
                .filter(order -> !"CANCELLED".equalsIgnoreCase(order.getStatus()))
                .toList();
        for (Order order : marketOrders) {
            if (!"PENDING".equals(order.getStatus())) {
                throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK);
            }
            for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
                Product product = productRepository.findById(item.getProduct().getId())
                        .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK));
                if (product.getPrice().compareTo(item.getUnitPrice()) != 0) {
                    throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_PRICE_CHANGED);
                }
            }
        }

        BigDecimal customSubtotal = activeCustoms.stream().map(this::resolveCustomLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal marketSubtotal = OrderPricing.marketplaceSubtotal(marketOrders);
        BigDecimal subtotal = customSubtotal.add(marketSubtotal);
        BigDecimal shippingFee = OrderPricing.checkoutShippingFee(checkoutOrder, this::resolveDefaultShippingFee);
        BenefitScope scope = activeCustoms.isEmpty() ? BenefitScope.MARKETPLACE
                : (marketOrders.isEmpty() ? BenefitScope.CUSTOM : BenefitScope.MIXED);

        SubscriptionBenefitService.AppliedBenefitSnapshot snapshot;
        if (subscriptionBenefitService != null) {
            snapshot = subscriptionBenefitService.reserveAndApplyBenefits(user,
                    SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER, id, scope, subtotal,
                    shippingFee, customSubtotal, marketSubtotal, preview ->
                            validateCheckoutPrice(checkoutOrder, request, paymentMethod,
                                    OrderPricing.fromPreview(version, customSubtotal, marketSubtotal, preview)));
        } else {
            PricingBreakdownDTO estimate = OrderPricing.estimate(null, user, scope, version, customSubtotal,
                    marketSubtotal, shippingFee);
            validateCheckoutPrice(checkoutOrder, request, paymentMethod, estimate);
            snapshot = SubscriptionBenefitService.AppliedBenefitSnapshot.empty(subtotal, shippingFee);
        }

        checkoutOrder.setItemsSubtotal(snapshot.itemsSubtotal());
        checkoutOrder.setShippingFee(snapshot.baseShippingFee());
        checkoutOrder.setDiscountAmount(snapshot.discountAmount());
        checkoutOrder.setShippingDiscountAmount(snapshot.shippingDiscountAmount());
        checkoutOrder.setAppliedSubscriptionId(snapshot.appliedSubscriptionId());
        checkoutOrder.setAppliedSubscriptionName(snapshot.appliedSubscriptionName());
        checkoutOrder.setPriorityProduction(snapshot.priorityProduction());
        checkoutOrder.setTotalAmount(snapshot.finalTotal());
        checkoutOrder.setPaymentMethod(paymentMethod);
        Instant now = Instant.now();
        checkoutOrder.setUpdatedAt(now);

        boolean zeroAmountSettlement = snapshot.fullyDiscounted();
        if (zeroAmountSettlement) {
            settleZeroAmountCheckout(checkoutOrder, now);
        } else if ("PAYOS".equals(paymentMethod)) {
            checkoutOrder.setStatus("CONFIRMED");
            prepareCheckoutPayment(checkoutOrder);
        } else {
            checkoutOrder.setStatus("PROCESSING");
        }
        checkoutOrderRepository.save(checkoutOrder);
        distributeSnapshotToChildren(checkoutOrder, activeCustoms, marketOrders, snapshot);

        for (CustomOrder custom : activeCustoms) {
            custom.setStatus(zeroAmountSettlement ? "PAID" : "ACCEPTED");
            custom.setUpdatedAt(now);
            customOrderRepository.save(custom);
        }
        if (zeroAmountSettlement || "COD".equals(paymentMethod)) {
            for (Order order : marketOrders) {
                if (zeroAmountSettlement) {
                    order.setStatus("PAID");
                    if (order.getSubtotalAmount() == null) {
                        order.setSubtotalAmount(order.getTotalAmount());
                    }
                    order.setTotalAmount(BigDecimal.ZERO);
                    order.setPaymentDueAt(null);
                    order.setUpdatedAt(now);
                    orderRepository.save(order);
                }
                inventoryService.confirmReservation(order.getId());
            }
        }
        return toDTO(checkoutOrder, null);
    }

    private void settleZeroAmountCheckout(CheckoutOrder checkout, Instant settledAt) {
        checkout.setZeroAmountSettledAt(settledAt);
        checkout.setStatus("PAID");
        checkout.setPaymentExpiresAt(null);
        checkout.setReservationExpiresAt(null);
        subscriptionBenefitService.commitAppliedBenefits(
                SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER, checkout.getId());
    }

    private void prepareCheckoutPayment(CheckoutOrder checkout) {
        if (paymentRepository == null) {
            return;
        }
        Payment payment = new Payment();
        payment.setCheckoutOrder(checkout);
        QuotePayment.prepare(paymentRepository, payment, checkout.getTotalAmount());
        checkout.setPaymentExpiresAt(payment.getExpiresAt());
    }

    private void validateCheckoutPrice(CheckoutOrder checkout, CheckoutOrderConfirmRequestDTO request,
                                       String paymentMethod, PricingBreakdownDTO estimate) {
        if (request.expectedTotalPayable() == null
                || request.expectedTotalPayable().compareTo(estimate.totalPayable()) != 0) {
            throw checkoutConflict(checkout, CheckoutOrderErrorCode.CONFIRMATION_PRICE_CHANGED, estimate);
        }
        if ("PAYOS".equals(paymentMethod) && estimate.totalPayable().signum() != 0) {
            QuotePayment.requirePayable(estimate.totalPayable());
        }
    }

    private ApiException checkoutConflict(CheckoutOrder checkout, CheckoutOrderErrorCode error,
                                          PricingBreakdownDTO pricing) {
        return pricing == null ? new ApiException(error) : new ApiException(error, Map.of(
                "id", checkout.getId(), "summaryVersion", pricing.quoteVersion(), "pricingBreakdown", pricing));
    }

    private CheckoutOrderResponseDTO toDTO(CheckoutOrder order, String paymentLinkUrl) {
        List<CustomOrder> customs = customOrderRepository.findByCheckoutOrderId(order.getId());
        List<Order> orders = orderRepository.findByCheckoutOrderId(order.getId());

        Payment payment = paymentRepository != null
                ? paymentRepository.findByCheckoutOrderId(order.getId()).orElse(null)
                : null;
        String paymentMethod = order.getPaymentMethod() != null
                ? order.getPaymentMethod()
                : (payment != null ? payment.getGateway() : "PAYOS");
        boolean zeroAmountSettlement = order.getZeroAmountSettledAt() != null;
        String paymentStatus = zeroAmountSettlement ? "SUCCESS" : payment != null
                ? payment.getStatus()
                : ("PAID".equals(order.getStatus()) ? "SUCCESS" : "PENDING");

        String resolvedOrderType = OrderPricing.checkoutOrderType(order, !customs.isEmpty(), !orders.isEmpty());

        List<CustomOrderDetailResponseDTO> customDetailDTOs = customs.stream()
                .map(c -> toCustomDetailDTO(c, paymentStatus, payment, zeroAmountSettlement))
                .toList();

        List<CustomOrderResponseDTO> customDTOs = customs.stream().map(c -> CustomOrderResponseDTO.builder()
                .id(c.getId())
                .buyerId(c.getBuyer().getId())
                .buyerName(c.getBuyer().getFullName())
                .requirements(c.getRequirements())
                .attachmentUrl(c.getAttachmentUrl())
                .unitQuotedPrice(c.getUnitQuotedPrice())
                .quotedPrice(c.getQuotedPrice())
                .quoteNote(c.getQuoteNote())
                .quotedAt(c.getQuotedAt())
                .checkoutOrderId(c.getCheckoutOrder() != null ? c.getCheckoutOrder().getId() : null)
                .status(c.getStatus())
                    .createdAt(c.getCreatedAt())
                    .updatedAt(c.getUpdatedAt())
                    .subtotalAmount(c.getSubtotalAmount())
                    .shippingFee(c.getShippingFee())
                    .discountAmount(c.getDiscountAmount())
                    .shippingDiscountAmount(c.getShippingDiscountAmount())
                    .appliedSubscriptionId(c.getAppliedSubscriptionId())
                    .appliedSubscriptionName(c.getAppliedSubscriptionName())
                    .priorityProduction(c.getPriorityProduction())
                    .shippingAddress(c.getShippingAddress())
                    .cancelReason(c.getCancelReason())
                    .cancelledByRole(c.getCancelledByRole())
                    .cancelledAt(c.getCancelledAt())
                    .build()).toList();

        List<OrderResponseDTO> orderDTOs = orders.stream().map(o -> {
            List<OrderItem> oItems = orderItemRepository.findByOrderId(o.getId());
            List<OrderItemResponseDTO> itemDTOs = oItems.stream().map(oi -> OrderItemResponseDTO.builder()
                    .id(oi.getId())
                    .productId(oi.getProduct() != null ? oi.getProduct().getId() : null)
                    .productTitle(oi.getProduct() != null ? oi.getProduct().getTitle() : "Sản phẩm")
                    .quantity(oi.getQuantity())
                    .unitPrice(oi.getUnitPrice())
                    .subTotal(oi.getUnitPrice() != null ? oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())) : BigDecimal.ZERO)
                    .color(oi.getColor())
                    .engravingText(oi.getEngravingText())
                    .build()).toList();

            return OrderResponseDTO.builder()
                    .id(o.getId())
                    .buyerId(o.getBuyer() != null ? o.getBuyer().getId() : null)
                    .buyerName(o.getBuyer() != null ? o.getBuyer().getFullName() : "Khách hàng")
                    .sellerId(o.getSeller() != null ? o.getSeller().getId() : null)
                    .sellerName(o.getSeller() != null ? o.getSeller().getFullName() : "PrintHub 3D Platform")
                    .totalAmount(o.getTotalAmount())
                    .commissionFee(o.getCommissionFee())
                    .status(o.getStatus())
                    .paymentStatus(o.resolveZeroAmountSettledAt() != null ? "SUCCESS" : null)
                    .items(itemDTOs)
                    .createdAt(o.getCreatedAt())
                    .updatedAt(o.getUpdatedAt())
                    .subtotalAmount(o.getSubtotalAmount())
                    .shippingFee(o.getShippingFee())
                    .discountAmount(o.getDiscountAmount())
                    .shippingDiscountAmount(o.getShippingDiscountAmount())
                    .appliedSubscriptionId(o.getAppliedSubscriptionId())
                    .appliedSubscriptionName(o.getAppliedSubscriptionName())
                    .priorityProduction(o.getPriorityProduction())
                    .build();
        }).toList();

        List<CustomOrder> activeCustoms = customs.stream()
                .filter(c -> !"CANCELLED".equalsIgnoreCase(c.getStatus()))
                .toList();
        boolean allCustomQuoted = !customs.isEmpty()
                ? (!activeCustoms.isEmpty() && activeCustoms.stream().allMatch(c -> resolveCustomLineTotal(c).compareTo(BigDecimal.ZERO) > 0 && !"REQUESTED".equalsIgnoreCase(c.getStatus())))
                : true;
        boolean readyForConfirm = ("PENDING_QUOTE".equals(order.getStatus()) || "QUOTED".equals(order.getStatus())) && allCustomQuoted;

        BigDecimal computedCustomSubtotal = activeCustoms.stream()
                .map(this::resolveCustomLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal computedMarketSubtotal = OrderPricing.marketplaceSubtotal(orders);
        BigDecimal resolvedItemsSubtotal = order.getItemsSubtotal() != null
                ? order.getItemsSubtotal()
                : computedCustomSubtotal.add(computedMarketSubtotal);
        PricingBreakdownDTO pricing = checkoutPricing(order, readyForConfirm, computedCustomSubtotal,
                computedMarketSubtotal, resolvedItemsSubtotal, resolvedOrderType);

        User currentUser = getCurrentUserSafely();
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        boolean isOwner = currentUser != null && order.getUser() != null && currentUser.getId().equals(order.getUser().getId());
        OrderTransitionPolicy.TransitionEvaluation eval = evaluateParentActions(order, resolvedOrderType, paymentMethod, paymentStatus, isAdmin, isOwner);

        String resolvedLink = paymentLinkUrl;
        if (resolvedLink == null && "CONFIRMED".equals(order.getStatus())
                && payment != null && "PENDING".equals(payment.getStatus())
                && payment.getExpiresAt() != null && payment.getExpiresAt().isAfter(Instant.now())) {
            resolvedLink = payment.getCheckoutUrl();
        }

        return CheckoutOrderResponseDTO.builder()
                .id(order.getId())
                .userId(order.getUser() != null ? order.getUser().getId() : null)
                .buyerName(order.getUser() != null ? order.getUser().getFullName() : null)
                .recipientName(order.getRecipientName() != null ? order.getRecipientName() : (order.getUser() != null ? order.getUser().getFullName() : null))
                .recipientPhone(order.getRecipientPhone() != null ? order.getRecipientPhone() : (order.getUser() != null ? order.getUser().getPhone() : null))
                .orderType(resolvedOrderType)
                .shippingAddress(order.getShippingAddress())
                .shippingMode(order.getShippingMode())
                .status(order.getStatus())
                .itemsSubtotal(resolvedItemsSubtotal)
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .cancelReason(order.getCancelReason())
                .summaryVersion(order.getSummaryVersion())
                .itemCount(customs.size() + orders.size())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .customOrders(customDTOs)
                .customOrderDetails(customDetailDTOs)
                .marketplaceOrders(orderDTOs)
                .allowedActions(eval.allowedActions())
                .blockedActions(eval.blockedActions())
                .allCustomQuoted(allCustomQuoted)
                .readyForConfirm(readyForConfirm)
                .paymentLinkUrl(resolvedLink)
                .discountAmount(order.getDiscountAmount())
                .shippingDiscountAmount(order.getShippingDiscountAmount())
                .appliedSubscriptionId(order.getAppliedSubscriptionId())
                .appliedSubscriptionName(order.getAppliedSubscriptionName())
                .priorityProduction(order.getPriorityProduction())
                .cancelledByRole(order.getCancelledByRole())
                .cancelledAt(order.getCancelledAt())
                .pricingBreakdown(pricing)
                .paymentEvidence(PaymentEvidenceResponseDTO.from(payment, order.getZeroAmountSettledAt()))
                .build();
    }

    private PricingBreakdownDTO checkoutPricing(CheckoutOrder order, boolean readyForConfirm,
                                                BigDecimal customSubtotal, BigDecimal marketSubtotal,
                                                BigDecimal itemsSubtotal, String orderType) {
        String status = order.getStatus();
        if (status == null || "CANCELLED".equalsIgnoreCase(status)) {
            return null;
        }
        Long version = order.getSummaryVersion() != null ? order.getSummaryVersion() : 1L;
        if (Set.of("PENDING_QUOTE", "QUOTED").contains(status)) {
            BigDecimal shippingFee = OrderPricing.checkoutShippingFee(order, this::resolveDefaultShippingFee);
            if (!readyForConfirm) {
                return OrderPricing.pendingQuote(version, customSubtotal, marketSubtotal, shippingFee);
            }
            return OrderPricing.estimateCheckout(subscriptionBenefitService, order, orderType,
                    version, customSubtotal, marketSubtotal, shippingFee);
        }
        return OrderPricing.locked(version, customSubtotal, marketSubtotal, itemsSubtotal, order.getDiscountAmount(),
                order.getShippingFee(), order.getShippingDiscountAmount(), order.getTotalAmount(),
                order.getAppliedSubscriptionId(), order.getAppliedSubscriptionName(), order.getPriorityProduction(),
                order.getZeroAmountSettledAt() != null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutOrderResponseDTO> getAdminCheckoutOrders(String orderType, String status, String query) {
        List<CheckoutOrder> all = (orderType != null && !orderType.isBlank())
                ? checkoutOrderRepository.findByOrderTypeOrderByCreatedAtDesc(orderType.trim().toUpperCase())
                : checkoutOrderRepository.findAllByOrderByCreatedAtDesc();

        String normalizedStatus = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        String normalizedQ = query != null && !query.isBlank() ? query.trim().toLowerCase() : null;

        return all.stream()
                .filter(co -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(co.getStatus()))
                .filter(co -> {
                    if (normalizedQ == null) return true;
                    String idStr = co.getId() != null ? co.getId().toString().toLowerCase() : "";
                    String buyerName = co.getUser() != null && co.getUser().getFullName() != null
                            ? co.getUser().getFullName().toLowerCase() : "";
                    String recipientName = co.getRecipientName() != null ? co.getRecipientName().toLowerCase() : "";
                    String address = co.getShippingAddress() != null ? co.getShippingAddress().toLowerCase() : "";
                    if (idStr.contains(normalizedQ) || buyerName.contains(normalizedQ)
                            || recipientName.contains(normalizedQ) || address.contains(normalizedQ)) {
                        return true;
                    }
                    return customOrderRepository.findByCheckoutOrderId(co.getId()).stream().anyMatch(c ->
                            (c.getId() != null && c.getId().toString().toLowerCase().contains(normalizedQ))
                            || (c.getCustomName() != null && c.getCustomName().toLowerCase().contains(normalizedQ))
                            || (c.getCustomStudentId() != null && c.getCustomStudentId().toLowerCase().contains(normalizedQ))
                            || (c.getRulerModel() != null && c.getRulerModel().toLowerCase().contains(normalizedQ))
                    );
                })
                .map(co -> toDTO(co, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutOrderResponseDTO getAdminCheckoutOrder(UUID id) {
        CheckoutOrder co = checkoutOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));
        return toDTO(co, null);
    }

    @Override
    public CheckoutOrderResponseDTO batchQuoteCheckoutOrder(User admin, UUID id, CheckoutOrderBatchQuoteRequestDTO request) {
        CheckoutOrder co = checkoutOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));

        String currentParentStatus = co.getStatus() != null ? co.getStatus().toUpperCase() : "";
        if (!Set.of("PENDING_QUOTE", "QUOTED").contains(currentParentStatus)) {
            throw new ApiException(
                    CheckoutOrderErrorCode.INVALID_ORDER_TRANSITION,
                    "Không thể báo giá hoặc chỉnh sửa báo giá khi đơn hàng đang ở trạng thái " + co.getStatus()
            );
        }

        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Danh sách báo giá từng sản phẩm không được để trống");
        }

        List<CustomOrder> allCustoms = customOrderRepository.findByCheckoutOrderId(id);
        List<CustomOrder> activeCustoms = allCustoms.stream()
                .filter(c -> !"CANCELLED".equalsIgnoreCase(c.getStatus()))
                .toList();
        if (activeCustoms.isEmpty()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn hàng không có sản phẩm in custom nào đang hoạt động để báo giá");
        }

        Map<UUID, CustomOrder> activeMap = activeCustoms.stream()
                .collect(Collectors.toMap(CustomOrder::getId, c -> c));

        // Kiểm tra toàn bộ payload trước khi thay đổi DB để đảm bảo tính nguyên tử (atomic)
        Set<UUID> seenIds = new HashSet<>();
        for (CheckoutOrderBatchQuoteRequestDTO.ItemQuote itemQuote : request.items()) {
            if (itemQuote == null || itemQuote.customOrderId() == null) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "ID đơn in custom không hợp lệ");
            }
            if (!seenIds.add(itemQuote.customOrderId())) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Bị trùng lặp sản phẩm trong yêu cầu báo giá: " + itemQuote.customOrderId());
            }
            CustomOrder target = activeMap.get(itemQuote.customOrderId());
            if (target == null) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Sản phẩm " + itemQuote.customOrderId() + " không thuộc đơn cha hoặc đã bị hủy");
            }
            if (itemQuote.unitPrice() == null || itemQuote.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn giá sản phẩm phải lớn hơn 0 VND");
            }
            String childStatus = target.getStatus() != null ? target.getStatus().toUpperCase() : "";
            if (!Set.of("REQUESTED", "QUOTED").contains(childStatus)) {
                throw new ApiException(
                        CheckoutOrderErrorCode.INVALID_ORDER_TRANSITION,
                        "Sản phẩm " + itemQuote.customOrderId() + " đang ở trạng thái " + target.getStatus() + ", không thể báo giá lại"
                );
            }
        }

        if (seenIds.size() != activeMap.size() || !seenIds.containsAll(activeMap.keySet())) {
            throw new ApiException(
                    CommonErrorCode.INVALID_INPUT,
                    "Vui lòng nhập đầy đủ đơn giá cho toàn bộ " + activeMap.size() + " sản phẩm in custom đang hoạt động trong đơn"
            );
        }

        Instant now = Instant.now();
        BigDecimal customSubtotal = BigDecimal.ZERO;
        boolean quoteChanged = !"QUOTED".equals(currentParentStatus);
        for (CheckoutOrderBatchQuoteRequestDTO.ItemQuote itemQuote : request.items()) {
            CustomOrder target = activeMap.get(itemQuote.customOrderId());
            int qty = OrderPricing.quoteQuantity(target);
            BigDecimal unitPrice = itemQuote.unitPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            String normalizedNote = itemQuote.note() != null && !itemQuote.note().isBlank() ? itemQuote.note().trim() : null;
            customSubtotal = customSubtotal.add(lineTotal);
            if (!OrderPricing.quoteChanged(target, unitPrice, lineTotal, normalizedNote)) {
                continue;
            }
            quoteChanged = true;

            target.setUnitQuotedPrice(unitPrice);
            target.setQuotedPrice(lineTotal);
            target.setQuoteNote(normalizedNote);
            target.setQuotedAt(now);
            target.setProcessedBy(admin);
            target.setStatus("QUOTED");
            target.setUpdatedAt(now);
            customOrderRepository.save(target);
        }

        if (!quoteChanged) {
            return toDTO(co, null);
        }
        List<Order> marketOrders = orderRepository.findByCheckoutOrderId(id);
        BigDecimal marketSubtotal = OrderPricing.marketplaceSubtotal(marketOrders);

        BigDecimal itemsSubtotal = customSubtotal.add(marketSubtotal);
        BigDecimal shippingFee = OrderPricing.checkoutShippingFee(co, this::resolveDefaultShippingFee);
        BigDecimal totalAmount = itemsSubtotal.add(shippingFee);

        co.setItemsSubtotal(itemsSubtotal);
        co.setShippingFee(shippingFee);
        co.setTotalAmount(totalAmount);
        co.setStatus("QUOTED");
        co.setSummaryVersion((co.getSummaryVersion() != null ? co.getSummaryVersion() : 1L) + 1L);
        co.setUpdatedAt(now);
        checkoutOrderRepository.save(co);

        CheckoutOrderResponseDTO response = toDTO(co, null);
        if (emailEvents != null) {
            emailEvents.enqueueQuotation("CHECKOUT_ORDER", id, co.getUser(), response.pricingBreakdown(), activeCustoms);
        }
        if (notificationService != null && co.getUser() != null) {
            notificationService.sendNotification(
                    co.getUser(),
                    "Đơn in 3D #" + id.toString().substring(0, 8) + " đã có báo giá",
                    "Xưởng đã báo giá chi tiết cho " + activeCustoms.size() + " sản phẩm in 3D. Tổng tạm tính: " + totalAmount + " VND (đã gồm phí ship).",
                    "CUSTOM_ORDER",
                    "/orders/" + id
            );
        }

        return response;
    }

    @Override
    public CheckoutOrderResponseDTO updateAdminCheckoutOrderStatus(User admin, UUID id, AdminCheckoutOrderStatusRequestDTO request) {
        if (request == null || request.getEffectiveTarget() == null || request.getEffectiveTarget().isBlank()) {
            throw new ApiException(com.fpt.printhub_3d.common.exception.CommonErrorCode.INVALID_INPUT,
                    "Trạng thái đích không được để trống");
        }

        CheckoutOrder co = checkoutOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));

        List<CustomOrder> customs = customOrderRepository.findByCheckoutOrderId(id);
        List<Order> orders = orderRepository.findByCheckoutOrderId(id);
        Payment payment = paymentRepository != null
                ? paymentRepository.findByCheckoutOrderId(id).orElse(null)
                : null;

        String paymentMethod = co.getPaymentMethod() != null
                ? co.getPaymentMethod()
                : (payment != null ? payment.getGateway() : "PAYOS");
        String paymentStatus = payment != null ? payment.getStatus() : ("PAID".equals(co.getStatus()) ? "SUCCESS" : "PENDING");

        String nextStatus = normalizeCheckoutTarget(request.getEffectiveTarget());
        String current = co.getStatus() != null ? co.getStatus().toUpperCase() : "PENDING_QUOTE";
        BigDecimal codCollectedAmount = request.codCollectedAmount();
        if (codCollectedAmount != null && (!"COD".equalsIgnoreCase(paymentMethod) || !"COMPLETED".equals(nextStatus))) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chỉ xác nhận thu COD khi hoàn tất đơn COD.");
        }

        if (current.equals(nextStatus)) {
            if (codCollectedAmount != null) {
                evaluateCompletionReward(id, codCollectedAmount, admin);
            }
            return toDTO(co, null);
        }

        boolean isPaid = co.getZeroAmountSettledAt() != null || "PAID".equals(current)
                || Set.of("PREPARING", "PRINTING", "SHIPPING", "COMPLETED").contains(current)
                || (payment != null && "SUCCESS".equalsIgnoreCase(paymentStatus));

        if ("CANCELLED".equals(nextStatus)) {
            if (payment != null && Payment.PENDING_RECONCILIATION.equals(payment.getStatus())) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Giao dịch đang chờ đối soát.");
            }
            if (isPaid) {
                throw new ApiException(CheckoutOrderErrorCode.CANNOT_CANCEL_CONFIRMED_ORDER);
            }
            String reason = request.getEffectiveCancelReason();
            if (reason == null || reason.length() < 5) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT,
                        "Vui lòng nhập lý do hủy đơn (tối thiểu 5 ký tự).");
            }
            if (payment != null && "PENDING".equals(payment.getStatus()) && "PAYOS".equals(payment.getGateway())
                    && payment.getTransactionId() != null) {
                paymentService.cancelQuotePayment(payment.getTransactionId(), reason);
            }
            Instant cancelTime = Instant.now();
            co.setStatus("CANCELLED");
            co.setCancelReason(reason);
            co.setCancelledByRole("ADMIN");
            co.setCancelledById(admin != null ? admin.getId() : null);
            co.setCancelledAt(cancelTime);
            co.setUpdatedAt(cancelTime);
            checkoutOrderRepository.save(co);
            if (subscriptionBenefitService != null) {
                subscriptionBenefitService.releaseReservedBenefits(
                        SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER, co.getId());
            }

            for (CustomOrder c : customs) {
                if (!"CANCELLED".equalsIgnoreCase(c.getStatus())) {
                    c.setStatus("CANCELLED");
                    c.setCancelReason(reason);
                    c.setCancelledByRole("ADMIN");
                    c.setCancelledById(admin != null ? admin.getId() : null);
                    c.setCancelledAt(cancelTime);
                    c.setUpdatedAt(cancelTime);
                    customOrderRepository.save(c);
                }
            }
            for (Order o : orders) {
                if (!"CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    o.setStatus("CANCELLED");
                    o.setUpdatedAt(cancelTime);
                    orderRepository.save(o);
                    inventoryService.releaseActiveReservations(o.getId(), reason, "CHECKOUT_CANCEL_" + id);
                }
            }
        } else {
            if ("PAYOS".equalsIgnoreCase(paymentMethod) && !isPaid) {
                throw new ApiException(CheckoutOrderErrorCode.CHECKOUT_PAYMENT_REQUIRED,
                        "Đơn hàng PayOS chưa hoàn tất thanh toán, không thể chuyển sang trạng thái " + nextStatus);
            }
            validateParentForwardTransition(current, nextStatus);

            Instant transitionAt = Instant.now();
            co.setStatus(nextStatus);
            co.setUpdatedAt(transitionAt);
            if ("COMPLETED".equals(nextStatus) && co.getCompletedAt() == null) {
                co.setCompletedAt(transitionAt);
            }
            checkoutOrderRepository.save(co);

            for (CustomOrder c : customs) {
                if (!"CANCELLED".equalsIgnoreCase(c.getStatus())) {
                    c.setStatus(nextStatus);
                    c.setUpdatedAt(transitionAt);
                    if ("COMPLETED".equals(nextStatus) && c.getCompletedAt() == null) {
                        c.setCompletedAt(transitionAt);
                    }
                    customOrderRepository.save(c);
                }
            }
            String marketplaceTarget = "PRINTING".equals(nextStatus) ? "PREPARING" : nextStatus;
            for (Order o : orders) {
                if (!"CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    o.setStatus(marketplaceTarget);
                    o.setUpdatedAt(transitionAt);
                    if ("COMPLETED".equals(marketplaceTarget) && o.getCompletedAt() == null) {
                        o.setCompletedAt(transitionAt);
                    }
                    orderRepository.save(o);
                }
            }
            if ("COMPLETED".equals(nextStatus)) {
                evaluateCompletionReward(id, codCollectedAmount, admin);
            }
        }

        if (notificationService != null && co.getUser() != null) {
            if ("CANCELLED".equals(nextStatus)) {
                String shortId = id.toString().length() >= 8 ? id.toString().substring(0, 8) : id.toString();
                notificationService.sendNotification(
                        co.getUser(),
                        "Yêu cầu báo giá đã bị hủy",
                        "Đơn hàng #" + shortId + " đã bị xưởng hủy. Lý do: " + co.getCancelReason(),
                        "CUSTOM_ORDER",
                        "/orders/" + id
                );
            } else {
                notificationService.sendNotification(
                        co.getUser(),
                        "Cập nhật tiến độ đơn hàng",
                        "Đơn hàng #" + id.toString().substring(0, 8) + " đã chuyển sang trạng thái: " + nextStatus,
                        "CUSTOM_ORDER",
                        "/orders"
                );
            }
        }

        return toDTO(co, null);
    }

    private void evaluateCompletionReward(UUID checkoutId, BigDecimal collectedAmount, User admin) {
        if (orderRewardService != null) {
            if (collectedAmount == null) {
                orderRewardService.processCompletionReward(PaymentEvidenceService.SourceType.CHECKOUT_ORDER, checkoutId);
            } else {
                orderRewardService.completeCodAndReward(PaymentEvidenceService.SourceType.CHECKOUT_ORDER, checkoutId,
                        collectedAmount, admin);
            }
        } else if (collectedAmount != null) {
            paymentEvidenceService.recordCodCollection(PaymentEvidenceService.SourceType.CHECKOUT_ORDER, checkoutId,
                    collectedAmount, admin);
        }
    }

    private CustomOrder createChildCustomOrder(User user, CheckoutOrder checkoutOrder, CheckoutOrderCreateRequestDTO request, CartItem cItem) {
        JsonNode designJson = parseJsonSafely(cItem.getCustomDesign());

        String rulerModel = nonBlank(cItem.getCustomName(),
                textAt(designJson, "name"),
                textAt(designJson, "rulerId"),
                "Thước 3D Cá Nhân Hóa");
        String customName = nonBlank(
                nestedTextAt(designJson, "tag", "text"),
                cItem.getCustomName(),
                textAt(designJson, "name"));
        String customStudentId = nonBlank(
                textAt(designJson, "customStudentId"),
                textAt(designJson, "studentId"),
                nestedTextAt(designJson, "tag", "subtext"));
        String color = truncate(nonBlank(
                textAt(designJson, "color"),
                nestedTextAt(designJson, "tag", "color")), 100);
        String fontStyle = truncate(nonBlank(
                textAt(designJson, "fontStyle"),
                nestedTextAt(designJson, "tag", "font")), 100);

        String resolvedAttachmentUrl = resolveAttachmentUrl(cItem, designJson, rulerModel);

        CustomOrder customOrder = new CustomOrder();
        customOrder.setBuyer(user);
        customOrder.setCheckoutOrder(checkoutOrder);
        customOrder.setRulerModel(truncate(rulerModel, 100));
        customOrder.setCustomName(truncate(customName, 100));
        customOrder.setCustomStudentId(truncate(customStudentId, 50));
        customOrder.setColor(color);
        customOrder.setFontStyle(fontStyle);
        customOrder.setRequirements(cItem.getCustomDesign());
        customOrder.setAttachmentUrl(resolvedAttachmentUrl);
        customOrder.setQuantity(cItem.getQuantity() != null ? cItem.getQuantity() : 1);
        customOrder.setShippingAddress(request.shippingAddress());
        customOrder.setPaymentMethod("PAYOS");
        customOrder.setQuotedPrice(null);
        customOrder.setStatus("REQUESTED");
        customOrder.setCreatedAt(Instant.now());
        customOrder.setUpdatedAt(Instant.now());
        return customOrder;
    }

    private String resolveAttachmentUrl(CartItem cItem, JsonNode designJson, String nameHint) {
        List<String> candidates = new ArrayList<>();
        String stlFromJson = textAt(designJson, "stlUrl");
        if (stlFromJson != null) candidates.add(stlFromJson);
        if (cItem.getThumbnailUrl() != null) candidates.add(cItem.getThumbnailUrl());
        String previewFromJson = textAt(designJson, "previewImageUrl");
        if (previewFromJson != null) candidates.add(previewFromJson);

        for (String candidate : candidates) {
            String trimmed = candidate != null ? candidate.trim() : "";
            if (trimmed.isEmpty() || "custom-model".equalsIgnoreCase(trimmed)) {
                continue;
            }
            if ((trimmed.startsWith("http://") || trimmed.startsWith("https://")) && trimmed.length() <= 1000) {
                return trimmed;
            }
            if (trimmed.startsWith("data:") && cloudinaryService != null) {
                String uploaded = uploadDataUrlImage(trimmed, nameHint);
                if (uploaded != null) {
                    return uploaded;
                }
            }
        }
        return null;
    }

    private String uploadDataUrlImage(String dataUrl, String nameHint) {
        try {
            int commaIdx = dataUrl.indexOf(',');
            if (commaIdx < 0) return null;
            String header = dataUrl.substring(0, commaIdx);
            String base64 = dataUrl.substring(commaIdx + 1);
            String mimeType = "image/png";
            if (header.contains(":") && header.contains(";")) {
                mimeType = header.substring(header.indexOf(':') + 1, header.indexOf(';'));
            }
            byte[] decoded = Base64.getDecoder().decode(base64);
            String ext = mimeType.contains("jpeg") || mimeType.contains("jpg") ? "jpg" : "png";
            String filename = (nameHint != null ? nameHint.replaceAll("[^a-zA-Z0-9_-]", "_") : "preview") + "." + ext;
            MultipartFile file = new ByteArrayMultipartFile("file", filename, mimeType, decoded);
            return cloudinaryService.uploadImage(file);
        } catch (Exception e) {
            log.warn("Không thể upload data URL preview lên Cloudinary, giữ fallback null: {}", e.getMessage());
            return null;
        }
    }

    private CustomOrderDetailResponseDTO toCustomDetailDTO(CustomOrder order, String parentPaymentStatus,
                                                         Payment parentPayment, boolean zeroAmountSettlement) {
        String pMethod = order.getPaymentMethod() != null ? order.getPaymentMethod() : "PAYOS";
        Instant zeroAmountSettledAt = order.resolveZeroAmountSettledAt();
        String pStatus = parentPaymentStatus != null && (!zeroAmountSettlement || zeroAmountSettledAt != null)
                ? parentPaymentStatus : "PENDING";
        User currentUser = getCurrentUserSafely();
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        boolean isOwner = currentUser == null || (order.getBuyer() != null && currentUser.getId().equals(order.getBuyer().getId()));
        OrderTransitionPolicy.TransitionEvaluation eval = orderTransitionPolicy.evaluateActions(
                OrderTransitionPolicy.OrderType.CUSTOM,
                order.getStatus(),
                pMethod,
                pStatus,
                isAdmin,
                isOwner
        );
        return CustomOrderDetailResponseDTO.builder()
                .id(order.getId())
                .buyerId(order.getBuyer() != null ? order.getBuyer().getId() : null)
                .buyerName(order.getBuyer() != null ? order.getBuyer().getFullName() : "Khách hàng")
                .rulerModel(order.getRulerModel())
                .customName(order.getCustomName())
                .customStudentId(order.getCustomStudentId())
                .color(order.getColor())
                .fontStyle(order.getFontStyle())
                .requirements(order.getRequirements())
                .quantity(order.getQuantity() != null ? order.getQuantity() : 1)
                .shippingAddress(order.getShippingAddress())
                .attachmentUrl(order.getAttachmentUrl())
                .unitQuotedPrice(order.getUnitQuotedPrice())
                .quotedPrice(order.getQuotedPrice())
                .quoteNote(order.getQuoteNote())
                .quotedAt(order.getQuotedAt())
                .checkoutOrderId(order.getCheckoutOrder() != null ? order.getCheckoutOrder().getId() : null)
                .status(order.getStatus())
                .paymentMethod(pMethod)
                .paymentStatus(pStatus)
                .allowedActions(eval.allowedActions())
                .blockedActions(eval.blockedActions())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .subtotalAmount(order.getSubtotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .shippingDiscountAmount(order.getShippingDiscountAmount())
                .appliedSubscriptionId(order.getAppliedSubscriptionId())
                .appliedSubscriptionName(order.getAppliedSubscriptionName())
                .priorityProduction(order.getPriorityProduction())
                .cancelReason(order.getCancelReason())
                .cancelledByRole(order.getCancelledByRole())
                .cancelledAt(order.getCancelledAt())
                .paymentEvidence(PaymentEvidenceResponseDTO.from(parentPayment, zeroAmountSettledAt))
                .build();
    }

    private void distributeSnapshotToChildren(
            CheckoutOrder co,
            List<CustomOrder> activeCustoms,
            List<Order> marketOrders,
            SubscriptionBenefitService.AppliedBenefitSnapshot snap
    ) {
        if (snap == null) {
            return;
        }
        BigDecimal totalDiscount = snap.discountAmount() != null ? snap.discountAmount() : BigDecimal.ZERO;
        BigDecimal totalItemsSub = snap.itemsSubtotal() != null ? snap.itemsSubtotal() : BigDecimal.ZERO;
        BigDecimal remainingDisc = totalDiscount;

        int totalChildren = activeCustoms.size() + marketOrders.size();
        int idx = 0;

        for (CustomOrder c : activeCustoms) {
            idx++;
            BigDecimal cSub = resolveCustomLineTotal(c);
            BigDecimal cDisc = allocateShare(cSub, totalItemsSub, totalDiscount, remainingDisc, idx == totalChildren);
            remainingDisc = remainingDisc.subtract(cDisc).max(BigDecimal.ZERO);

            c.setSubtotalAmount(cSub);
            c.setDiscountAmount(cDisc);
            c.setAppliedSubscriptionId(snap.appliedSubscriptionId());
            c.setAppliedSubscriptionName(snap.appliedSubscriptionName());
            c.setPriorityProduction(snap.priorityProduction());
        }

        for (Order o : marketOrders) {
            idx++;
            BigDecimal oSub = o.getSubtotalAmount() != null
                    ? o.getSubtotalAmount()
                    : (o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO);
            BigDecimal oDisc = allocateShare(oSub, totalItemsSub, totalDiscount, remainingDisc, idx == totalChildren);
            remainingDisc = remainingDisc.subtract(oDisc).max(BigDecimal.ZERO);

            o.setSubtotalAmount(oSub);
            o.setDiscountAmount(oDisc);
            o.setAppliedSubscriptionId(snap.appliedSubscriptionId());
            o.setAppliedSubscriptionName(snap.appliedSubscriptionName());
            o.setPriorityProduction(snap.priorityProduction());
            o.setUpdatedAt(Instant.now());
            orderRepository.save(o);
        }
    }

    private BigDecimal allocateShare(
            BigDecimal lineSub,
            BigDecimal totalSub,
            BigDecimal totalDiscount,
            BigDecimal remainingDisc,
            boolean isLast
    ) {
        if (totalDiscount.compareTo(BigDecimal.ZERO) <= 0 || totalSub.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (isLast) {
            return remainingDisc.min(lineSub).max(BigDecimal.ZERO);
        }
        BigDecimal ratioDisc = totalDiscount.multiply(lineSub)
                .divide(totalSub, 0, java.math.RoundingMode.HALF_UP);
        return ratioDisc.min(remainingDisc).min(lineSub).max(BigDecimal.ZERO);
    }

    private OrderTransitionPolicy.TransitionEvaluation evaluateParentActions(
            CheckoutOrder order,
            String orderType,
            String paymentMethod,
            String paymentStatus,
            boolean isAdmin,
            boolean isOwner
    ) {
        String st = order.getStatus() != null ? order.getStatus().toUpperCase() : "PENDING_QUOTE";
        if ("MARKETPLACE".equals(orderType)) {
            if (isAdmin && "COD".equalsIgnoreCase(paymentMethod) && Set.of("CONFIRMED", "PROCESSING").contains(st)) {
                return new OrderTransitionPolicy.TransitionEvaluation(List.of("START_PREPARATION", "CANCEL"), List.of());
            }
            return orderTransitionPolicy.evaluateActions(
                    OrderTransitionPolicy.OrderType.MARKETPLACE,
                    st,
                    paymentMethod,
                    paymentStatus,
                    isAdmin,
                    isOwner
            );
        }
        List<String> allowed = new ArrayList<>();
        List<BlockedActionDTO> blocked = new ArrayList<>();
        boolean isPaid = "PAID".equals(st)
                || Set.of("PREPARING", "PRINTING", "SHIPPING", "COMPLETED").contains(st)
                || "SUCCESS".equalsIgnoreCase(paymentStatus);

        if (isAdmin) {
            if ("PAID".equals(st) || ("CONFIRMED".equals(st) && isPaid)) {
                allowed.add("START_PREPARATION");
            } else if ("PREPARING".equals(st)) {
                allowed.add("START_PRINTING");
            } else if ("PRINTING".equals(st)) {
                allowed.add("START_SHIPPING");
            } else if ("SHIPPING".equals(st)) {
                allowed.add("COMPLETE_DELIVERY");
            } else if ("CONFIRMED".equals(st) && "PAYOS".equalsIgnoreCase(paymentMethod)) {
                blocked.add(new BlockedActionDTO("START_PREPARATION", "PAYMENT_REQUIRED", "Chờ khách hàng hoàn tất thanh toán PayOS"));
            }
            if (Set.of("PENDING_QUOTE", "QUOTED", "CONFIRMED").contains(st) && !isPaid) {
                allowed.add("CANCEL");
            }
        } else if (isOwner && Set.of("PENDING_QUOTE", "QUOTED", "CONFIRMED").contains(st) && !isPaid) {
            allowed.add("CANCEL");
        }
        return new OrderTransitionPolicy.TransitionEvaluation(allowed, blocked);
    }

    private void notifyAdminsNewCustomCheckout(CheckoutOrder checkoutOrder, User buyer, int count) {
        try {
            List<User> admins = userRepository.findByRole(UserRole.ADMIN);
            for (User admin : admins) {
                notificationService.sendNotification(
                        admin,
                        "Yêu cầu báo giá in 3D mới",
                        "Khách hàng " + buyer.getFullName() + " vừa gửi đơn #"
                                + checkoutOrder.getId().toString().substring(0, 8) + " gồm " + count + " thiết kế custom.",
                        "CUSTOM_ORDER",
                        "/admin/custom-orders"
                );
            }
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo admin cho đơn custom {}: {}", checkoutOrder.getId(), e.getMessage());
        }
    }

    private String normalizeCheckoutTarget(String raw) {
        String upper = raw.trim().toUpperCase();
        return switch (upper) {
            case "START_PREPARATION" -> "PREPARING";
            case "START_PRINTING" -> "PRINTING";
            case "START_SHIPPING" -> "SHIPPING";
            case "COMPLETE_DELIVERY" -> "COMPLETED";
            case "CANCEL" -> "CANCELLED";
            default -> upper;
        };
    }

    private void validateParentForwardTransition(String current, String target) {
        boolean valid = switch (current) {
            case "PAID", "CONFIRMED", "PROCESSING" -> Set.of("PREPARING", "PRINTING").contains(target);
            case "PREPARING" -> Set.of("PRINTING", "SHIPPING").contains(target);
            case "PRINTING" -> "SHIPPING".equals(target);
            case "SHIPPING" -> "COMPLETED".equals(target);
            default -> false;
        };
        if (!valid) {
            throw new ApiException(com.fpt.printhub_3d.common.exception.CommonErrorCode.INVALID_INPUT,
                    "Không thể chuyển trạng thái đơn từ " + current + " sang " + target);
        }
    }

    private User getCurrentUserSafely() {
        try {
            return SecurityUtils.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode parseJsonSafely(String raw) {
        if (raw == null || !raw.trim().startsWith("{")) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String textAt(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String val = node.get(field).asText("").trim();
        return val.isEmpty() ? null : val;
    }

    private String nestedTextAt(JsonNode node, String objField, String field) {
        if (node == null || !node.has(objField) || !node.get(objField).isObject()) return null;
        return textAt(node.get(objField), field);
    }

    private String nonBlank(String... values) {
        for (String val : values) {
            if (val != null && !val.isBlank()) return val.trim();
        }
        return null;
    }

    private String truncate(String val, int maxLen) {
        if (val == null) return null;
        return val.length() <= maxLen ? val : val.substring(0, maxLen);
    }

    private record ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content)
            implements MultipartFile {
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content != null ? content.length : 0; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) throws IOException {
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(content);
            }
        }
    }
}
