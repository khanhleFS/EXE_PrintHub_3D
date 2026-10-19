package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CheckoutOrderErrorCode;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderItemResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.CheckoutOrderService;
import com.fpt.printhub_3d.service.PaymentService;
import com.fpt.printhub_3d.entity.IdempotencyRecord;
import com.fpt.printhub_3d.entity.InventoryReservation;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.repository.IdempotencyRecordRepository;
import com.fpt.printhub_3d.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
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
    private final InventoryReservationRepository inventoryReservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final com.fpt.printhub_3d.repository.StoreSettingRepository storeSettingRepository;
    private final com.fpt.printhub_3d.repository.UserSubscriptionRepository userSubscriptionRepository;
    private final com.fpt.printhub_3d.repository.PaymentRepository paymentRepository;

    private String computeSha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    @Override
    public CheckoutOrderResponseDTO createCheckoutOrder(User user, CheckoutOrderCreateRequestDTO request) {
        log.info("Tạo CheckoutOrder cho user: {}", user.getId());

        String createPayload = (request.selectedLineIds() != null ? request.selectedLineIds().stream().sorted().toList().toString() : "")
                + "|" + request.shippingMode()
                + "|" + request.shippingAddress()
                + "|" + request.paymentMethod();
        String currentHash = computeSha256(createPayload);

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existingRecord = idempotencyRecordRepository.findByUserIdAndActionKeyAndIdempotencyKey(
                    user.getId(), "CREATE_CHECKOUT_ORDER", request.idempotencyKey());
            if (existingRecord.isPresent()) {
                IdempotencyRecord rec = existingRecord.get();
                if (rec.getRequestHash() != null && !rec.getRequestHash().equals(currentHash)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key trùng lặp nhưng nội dung yêu cầu khác biệt");
                }
                var existing = checkoutOrderRepository.findFirstByUserIdAndIdempotencyKey(user.getId(), request.idempotencyKey());
                if (existing.isPresent()) {
                    log.info("Idempotent hit cho checkoutOrder user: {}, key: {}", user.getId(), request.idempotencyKey());
                    return toDTO(existing.get(), null);
                }
            } else {
                var existing = checkoutOrderRepository.findFirstByUserIdAndIdempotencyKey(user.getId(), request.idempotencyKey());
                if (existing.isPresent()) {
                    log.info("Idempotent hit cho checkoutOrder user: {}, key: {}", user.getId(), request.idempotencyKey());
                    return toDTO(existing.get(), null);
                }
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

        if ("COMMON".equalsIgnoreCase(shippingMode) && !customLines.isEmpty() && "COD".equalsIgnoreCase(request.paymentMethod())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT,
                    "Đơn hàng giao chung (COMMON) có sản phẩm custom 3D bắt buộc thanh toán trực tuyến qua PayOS (không hỗ trợ COD)");
        }

        // Lấy phí ship mặc định từ cài đặt cửa hàng (mặc định 15.000 VND) (G02)
        BigDecimal baseShippingFee = storeSettingRepository.findById("shippingFee")
                .map(s -> {
                    try {
                        BigDecimal v = new BigDecimal(s.getValue());
                        return v.compareTo(BigDecimal.ZERO) >= 0 ? v : BigDecimal.valueOf(15000);
                    } catch (Exception e) {
                        return BigDecimal.valueOf(15000);
                    }
                })
                .orElse(BigDecimal.valueOf(15000));

        List<UserSubscription> activeSubs = userSubscriptionRepository.findActiveSubscriptionsByUser(user);
        boolean hasFreeShipping = activeSubs.stream().anyMatch(s -> Boolean.TRUE.equals(s.getAppliedFreeShipping()));
        BigDecimal maxShippingDiscount = activeSubs.stream()
                .map(s -> s.getAppliedShippingDiscountAmount() != null ? s.getAppliedShippingDiscountAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal finalShippingFee;
        if (hasFreeShipping) {
            finalShippingFee = BigDecimal.ZERO;
        } else if (maxShippingDiscount.compareTo(BigDecimal.ZERO) > 0) {
            finalShippingFee = baseShippingFee.subtract(maxShippingDiscount).max(BigDecimal.ZERO);
        } else {
            finalShippingFee = baseShippingFee;
        }

        CheckoutOrder checkoutOrder = new CheckoutOrder();
        checkoutOrder.setUser(user);
        checkoutOrder.setShippingAddress(request.shippingAddress());
        checkoutOrder.setShippingMode(shippingMode);
        checkoutOrder.setShippingFee(finalShippingFee);

        if (!marketplaceLines.isEmpty()) {
            checkoutOrder.setMarketplaceShippingFee(finalShippingFee);
            checkoutOrder.setCustomShippingFee(BigDecimal.ZERO);
            checkoutOrder.setShippingFeeAssignedScope("MARKETPLACE");
        } else {
            checkoutOrder.setMarketplaceShippingFee(BigDecimal.ZERO);
            checkoutOrder.setCustomShippingFee(finalShippingFee);
            checkoutOrder.setShippingFeeAssignedScope("CUSTOM");
        }

        checkoutOrder.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod().toUpperCase() : "PAYOS");
        checkoutOrder.setSummaryVersion(1L);
        checkoutOrder.setIdempotencyKey(request.idempotencyKey());
        checkoutOrder.setCreatedAt(Instant.now());
        checkoutOrder.setUpdatedAt(Instant.now());

        BigDecimal marketSubtotal = marketplaceLines.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant now = Instant.now();
        Instant paymentExpiresAt = now.plus(java.time.Duration.ofMinutes(15));
        Instant reservationExpiresAt = paymentExpiresAt.plus(java.time.Duration.ofMinutes(5));
        checkoutOrder.setPaymentExpiresAt(paymentExpiresAt);
        checkoutOrder.setReservationExpiresAt(reservationExpiresAt);

        if ("SEPARATE".equalsIgnoreCase(shippingMode)) {
            if (!marketplaceLines.isEmpty()) {
                checkoutOrder.setMarketplaceStatus("CONFIRMED");
            }
            if (!customLines.isEmpty()) {
                checkoutOrder.setCustomStatus("PENDING_QUOTE");
                checkoutOrder.setStatus("PENDING_QUOTE");
            } else {
                checkoutOrder.setStatus("CONFIRMED");
            }
            checkoutOrder.setTotalAmount(marketSubtotal.add(checkoutOrder.getShippingFee()));
        } else {
            if (customLines.isEmpty()) {
                checkoutOrder.setStatus("CONFIRMED");
                checkoutOrder.setMarketplaceStatus("CONFIRMED");
                checkoutOrder.setTotalAmount(marketSubtotal.add(checkoutOrder.getShippingFee()));
            } else {
                checkoutOrder.setStatus("PENDING_QUOTE");
                checkoutOrder.setMarketplaceStatus("PENDING");
                checkoutOrder.setCustomStatus("PENDING_QUOTE");
                checkoutOrder.setTotalAmount(marketSubtotal.add(checkoutOrder.getShippingFee()));
            }
        }

        checkoutOrder = checkoutOrderRepository.save(checkoutOrder);

        // Giữ kho cho marketplace items nếu đơn hoặc nhóm marketplace ở trạng thái CONFIRMED
        boolean shouldReserveMarketplaceNow = customLines.isEmpty() || "SEPARATE".equalsIgnoreCase(shippingMode);
        if (shouldReserveMarketplaceNow && !marketplaceLines.isEmpty()) {
            for (CartItem ci : marketplaceLines) {
                Product p = productRepository.findLockedById(ci.getProduct().getId())
                        .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK));
                Integer reserved = inventoryReservationRepository.getActiveReservedQuantity(p.getId(), now);
                int available = p.getStock() - (reserved != null ? reserved : 0);
                if (available < ci.getQuantity()) {
                    throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK,
                            "Sản phẩm '" + p.getTitle() + "' không đủ số lượng tồn kho khả dụng");
                }

                InventoryReservation res = new InventoryReservation();
                res.setProduct(p);
                res.setCheckoutOrder(checkoutOrder);
                res.setQuantity(ci.getQuantity());
                res.setStatus("COD".equalsIgnoreCase(checkoutOrder.getPaymentMethod()) ? "COMMITTED" : "RESERVED");
                res.setExpiresAt(checkoutOrder.getReservationExpiresAt());
                res.setCreatedAt(now);
                res.setUpdatedAt(now);
                inventoryReservationRepository.save(res);

                if ("COD".equalsIgnoreCase(checkoutOrder.getPaymentMethod())) {
                    p.setStock(p.getStock() - ci.getQuantity());
                    productRepository.save(p);
                }
            }
            if ("COD".equalsIgnoreCase(checkoutOrder.getPaymentMethod())) {
                checkoutOrder.setMarketplaceStatus("PROCESSING");
                if (customLines.isEmpty()) {
                    checkoutOrder.setStatus("PROCESSING");
                }
                checkoutOrderRepository.save(checkoutOrder);

                Payment codPayment = new Payment();
                codPayment.setCheckoutOrder(checkoutOrder);
                codPayment.setGateway("COD");
                codPayment.setScope("MARKETPLACE");
                codPayment.setStatus("PENDING");
                BigDecimal ship = checkoutOrder.getMarketplaceShippingFee() != null ? checkoutOrder.getMarketplaceShippingFee() : (checkoutOrder.getShippingFee() != null ? checkoutOrder.getShippingFee() : BigDecimal.ZERO);
                codPayment.setAmount(marketSubtotal.add(ship));
                codPayment.setTransactionId("COD-" + checkoutOrder.getId().toString().substring(0, 8));
                codPayment.setCreatedAt(now);
                codPayment.setUpdatedAt(now);
                paymentRepository.save(codPayment);
            }
        }

        // Tạo CustomOrder cho các dòng thiết kế custom (luôn là REQUESTED, không lấy giá từ client)
        for (CartItem cItem : customLines) {
            CustomOrder customOrder = new CustomOrder();
            customOrder.setBuyer(user);
            customOrder.setCheckoutOrder(checkoutOrder);
            customOrder.setRulerModel(cItem.getCustomName() != null ? cItem.getCustomName() : "Thước 3D Cá Nhân Hóa");
            customOrder.setCustomName(cItem.getCustomName());
            customOrder.setRequirements(cItem.getCustomDesign());
            String attachmentUrl = cItem.getThumbnailUrl();
            if (attachmentUrl != null && (attachmentUrl.startsWith("data:") || attachmentUrl.length() > 500)) {
                attachmentUrl = null;
            }
            customOrder.setAttachmentUrl(attachmentUrl);
            customOrder.setQuantity(cItem.getQuantity() != null ? cItem.getQuantity() : 1);
            customOrder.setShippingAddress(request.shippingAddress());
            customOrder.setPaymentMethod("PAYOS"); // Bắt buộc PayOS cho custom
            customOrder.setQuotedPrice(null);      // Không lấy giá từ client
            customOrder.setUnitPrice(null);
            customOrder.setPriceVersion(2);        // Áp dụng đơn giá version 2
            customOrder.setStatus("REQUESTED");
            customOrder.setCreatedAt(Instant.now());
            customOrder.setUpdatedAt(Instant.now());
            customOrderRepository.save(customOrder);
        }

        // Tạo 1 Order Marketplace duy nhất (seller = null)
        if (!marketplaceLines.isEmpty()) {
            Order order = new Order();
            order.setBuyer(user);
            order.setSeller(null);
            order.setCheckoutOrder(checkoutOrder);
            order.setStatus("PENDING");
            order.setTotalAmount(marketSubtotal);
            order.setCommissionFee(BigDecimal.ZERO);
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());
            order = orderRepository.save(order);

            for (CartItem ci : marketplaceLines) {
                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setProduct(ci.getProduct());
                orderItem.setQuantity(ci.getQuantity());
                orderItem.setUnitPrice(ci.getProduct().getPrice());
                orderItemRepository.save(orderItem);
            }
        }

        // Xóa các dòng đã chọn khỏi giỏ hàng
        cartItemRepository.deleteAll(selectedItems);
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            try {
                IdempotencyRecord rec = new IdempotencyRecord();
                rec.setUser(user);
                rec.setActionKey("CREATE_CHECKOUT_ORDER");
                rec.setIdempotencyKey(request.idempotencyKey());
                rec.setRequestHash(currentHash);
                rec.setStatus("COMPLETED");
                rec.setCreatedAt(Instant.now());
                rec.setExpiresAt(Instant.now().plus(java.time.Duration.ofHours(24)));
                idempotencyRecordRepository.save(rec);
            } catch (Exception e) {
                log.warn("Không thể lưu idempotency record cho CREATE_CHECKOUT_ORDER: {}", e.getMessage());
            }
        }

        // Tách rời tạo đơn khỏi provider: Việc tạo link PayOS do API createPaymentLink thực hiện độc lập (Task 1.1)
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

        if ("CANCELLED".equals(checkoutOrder.getStatus())) {
            throw new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND);
        }

        // Chống lặp confirm nếu đã ở CONFIRMED (idempotent re-entry) (Task 1.4)
        if ("CONFIRMED".equals(checkoutOrder.getStatus())) {
            return toDTO(checkoutOrder, null);
        }

        String confirmPayload = id.toString() + "|" + request.summaryVersion() + "|" + request.paymentMethod();
        String currentConfirmHash = computeSha256(confirmPayload);

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existingRecord = idempotencyRecordRepository.findByUserIdAndActionKeyAndIdempotencyKey(
                    user.getId(), "CONFIRM_CHECKOUT_ORDER", request.idempotencyKey());
            if (existingRecord.isPresent()) {
                IdempotencyRecord rec = existingRecord.get();
                if (rec.getRequestHash() != null && !rec.getRequestHash().equals(currentConfirmHash)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key trùng lặp nhưng nội dung xác nhận khác biệt");
                }
                return toDTO(checkoutOrder, null);
            }
        }

        boolean isPendingQuoteOrQuoted = "PENDING_QUOTE".equals(checkoutOrder.getStatus()) || "QUOTED".equals(checkoutOrder.getStatus());
        boolean isSeparatePartiallyPaid = "SEPARATE".equalsIgnoreCase(checkoutOrder.getShippingMode())
                && "PARTIALLY_PAID".equals(checkoutOrder.getStatus())
                && ("QUOTED".equals(checkoutOrder.getCustomStatus()) || "PENDING_QUOTE".equals(checkoutOrder.getCustomStatus()));

        if (!isPendingQuoteOrQuoted && !isSeparatePartiallyPaid) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn không ở trạng thái chờ xác nhận báo giá");
        }

        if (request.summaryVersion() != null && !checkoutOrder.getSummaryVersion().equals(request.summaryVersion())) {
            throw new ApiException(CheckoutOrderErrorCode.CONFIRMATION_VERSION_CONFLICT);
        }

        List<CustomOrder> customList = customOrderRepository.findByCheckoutOrderId(checkoutOrder.getId());
        for (CustomOrder c : customList) {
            if (c.getQuotedPrice() == null) {
                throw new ApiException(CheckoutOrderErrorCode.CUSTOM_NOT_ALL_QUOTED);
            }
        }

        String chosenMethod = (request.paymentMethod() != null && !request.paymentMethod().isBlank())
                ? request.paymentMethod().toUpperCase()
                : (checkoutOrder.getPaymentMethod() != null ? checkoutOrder.getPaymentMethod() : "PAYOS");

        if ("COMMON".equalsIgnoreCase(checkoutOrder.getShippingMode()) && !customList.isEmpty() && "COD".equalsIgnoreCase(chosenMethod)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT,
                    "Đơn hàng giao chung (COMMON) có sản phẩm custom 3D bắt buộc thanh toán trực tuyến qua PayOS (không hỗ trợ COD)");
        }

        List<Order> orderList = orderRepository.findByCheckoutOrderId(checkoutOrder.getId());
        Instant now = Instant.now();
        Instant paymentExpiresAt = now.plus(java.time.Duration.ofMinutes(15));
        Instant reservationExpiresAt = paymentExpiresAt.plus(java.time.Duration.ofMinutes(5));

        // Kiểm tra và giữ kho cho marketplace items (tránh nhân đôi reservation nếu đã tạo lúc create)
        List<InventoryReservation> existingResList = inventoryReservationRepository.findByCheckoutOrderId(checkoutOrder.getId());
        if (existingResList.isEmpty()) {
            for (Order o : orderList) {
                List<OrderItem> items = orderItemRepository.findByOrderId(o.getId());
                for (OrderItem oi : items) {
                    Product p = productRepository.findLockedById(oi.getProduct().getId())
                            .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK));
                    if (p.getPrice().compareTo(oi.getUnitPrice()) != 0) {
                        throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_PRICE_CHANGED);
                    }
                    Integer reserved = inventoryReservationRepository.getActiveReservedQuantity(p.getId(), now);
                    int available = p.getStock() - (reserved != null ? reserved : 0);
                    if (available < oi.getQuantity()) {
                        throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK,
                                "Sản phẩm '" + p.getTitle() + "' không đủ số lượng tồn kho khả dụng");
                    }

                    InventoryReservation res = new InventoryReservation();
                    res.setProduct(p);
                    res.setCheckoutOrder(checkoutOrder);
                    res.setQuantity(oi.getQuantity());
                    res.setStatus("COD".equalsIgnoreCase(chosenMethod) ? "COMMITTED" : "RESERVED");
                    res.setExpiresAt(reservationExpiresAt);
                    res.setCreatedAt(now);
                    res.setUpdatedAt(now);
                    inventoryReservationRepository.save(res);

                    if ("COD".equalsIgnoreCase(chosenMethod)) {
                        p.setStock(p.getStock() - oi.getQuantity());
                        productRepository.save(p);
                    }
                }
            }
        } else {
            for (var r : existingResList) {
                if ("RESERVED".equals(r.getStatus())) {
                    if (r.getExpiresAt().isBefore(now)) {
                        Product p = productRepository.findLockedById(r.getProduct().getId())
                                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK));
                        Integer reserved = inventoryReservationRepository.getActiveReservedQuantity(p.getId(), now);
                        int available = p.getStock() - (reserved != null ? reserved : 0);
                        if (available < r.getQuantity()) {
                            throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK,
                                    "Sản phẩm '" + p.getTitle() + "' đã hết hàng trong thời gian chờ duyệt báo giá");
                        }
                    }
                    r.setExpiresAt(reservationExpiresAt);
                    r.setUpdatedAt(now);
                    inventoryReservationRepository.save(r);
                }
            }
        }

        // Tính tổng tiền sau báo giá
        BigDecimal customTotal = customList.stream()
                .filter(c -> !"CANCELLED".equals(c.getStatus()) && c.getQuotedPrice() != null)
                .map(c -> {
                    int q = c.getQuantity() != null && c.getQuantity() > 0 ? c.getQuantity() : 1;
                    return (c.getPriceVersion() != null && c.getPriceVersion() >= 2)
                            ? c.getQuotedPrice().multiply(BigDecimal.valueOf(q))
                            : c.getQuotedPrice();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal marketplaceTotal = orderList.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingFee = checkoutOrder.getShippingFee() != null ? checkoutOrder.getShippingFee() : BigDecimal.valueOf(15000);
        BigDecimal finalTotal = customTotal.add(marketplaceTotal).add(shippingFee);

        checkoutOrder.setTotalAmount(finalTotal);
        if ("SEPARATE".equalsIgnoreCase(checkoutOrder.getShippingMode())) {
            if ("PAID".equalsIgnoreCase(checkoutOrder.getMarketplaceStatus())) {
                checkoutOrder.setStatus("PARTIALLY_PAID");
            } else if ("PROCESSING".equalsIgnoreCase(checkoutOrder.getMarketplaceStatus())
                    || "SHIPPING".equalsIgnoreCase(checkoutOrder.getMarketplaceStatus())
                    || "COMPLETED".equalsIgnoreCase(checkoutOrder.getMarketplaceStatus())) {
                // Giữ nguyên trạng thái fulfillment của marketplace (COD hoặc đơn đã xử lý giao hàng)
                checkoutOrder.setStatus("PROCESSING");
            } else {
                checkoutOrder.setStatus("CONFIRMED");
                checkoutOrder.setMarketplaceStatus("CONFIRMED");
            }
            checkoutOrder.setCustomStatus("ACCEPTED");
        } else {
            checkoutOrder.setStatus("CONFIRMED");
            checkoutOrder.setCustomStatus("CONFIRMED");
            checkoutOrder.setMarketplaceStatus("CONFIRMED");
            checkoutOrder.setPaymentMethod(chosenMethod);
            if ("COD".equalsIgnoreCase(chosenMethod)) {
                checkoutOrder.setStatus("PROCESSING");
            }
        }
        checkoutOrder.setSummaryVersion(checkoutOrder.getSummaryVersion() + 1);
        checkoutOrder.setPaymentExpiresAt(paymentExpiresAt);
        checkoutOrder.setReservationExpiresAt(reservationExpiresAt);
        // Do not overwrite create idempotencyKey; confirm key is tracked in IdempotencyRecord below
        checkoutOrder.setUpdatedAt(Instant.now());
        checkoutOrderRepository.save(checkoutOrder);

        for (CustomOrder c : customList) {
            if (!"PAID".equals(c.getStatus())) {
                c.setStatus("ACCEPTED");
                c.setUpdatedAt(Instant.now());
                customOrderRepository.save(c);
            }
        }

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            try {
                IdempotencyRecord rec = new IdempotencyRecord();
                rec.setUser(user);
                rec.setActionKey("CONFIRM_CHECKOUT_ORDER");
                rec.setIdempotencyKey(request.idempotencyKey());
                rec.setRequestHash(currentConfirmHash);
                rec.setStatus("COMPLETED");
                rec.setCreatedAt(Instant.now());
                rec.setExpiresAt(Instant.now().plus(java.time.Duration.ofHours(24)));
                idempotencyRecordRepository.save(rec);
            } catch (Exception e) {
                log.warn("Không thể lưu idempotency record: {}", e.getMessage());
            }
        }

        return toDTO(checkoutOrder, null);
    }

    private CheckoutOrderResponseDTO toDTO(CheckoutOrder order, String paymentLinkUrl) {
        List<CustomOrder> customs = customOrderRepository.findByCheckoutOrderId(order.getId());
        List<Order> orders = orderRepository.findByCheckoutOrderId(order.getId());

        List<CustomOrderResponseDTO> customDTOs = customs.stream().map(c -> CustomOrderResponseDTO.builder()
                .id(c.getId())
                .buyerId(c.getBuyer().getId())
                .buyerName(c.getBuyer().getFullName())
                .requirements(c.getRequirements())
                .attachmentUrl(c.getAttachmentUrl())
                .quotedPrice(c.getQuotedPrice())
                .unitPrice(c.getUnitPrice())
                .quantity(c.getQuantity())
                .priceVersion(c.getPriceVersion())
                .rulerModel(c.getRulerModel())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build()).toList();

        List<OrderResponseDTO> orderDTOs = orders.stream().map(o -> {
            List<OrderItem> oItems = orderItemRepository.findByOrderId(o.getId());
            List<OrderItemResponseDTO> itemDTOs = oItems.stream().map(oi -> OrderItemResponseDTO.builder()
                    .id(oi.getId())
                    .productId(oi.getProduct().getId())
                    .productTitle(oi.getProduct().getTitle())
                    .quantity(oi.getQuantity())
                    .unitPrice(oi.getUnitPrice())
                    .subTotal(oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())))
                    .color(oi.getColor())
                    .engravingText(oi.getEngravingText())
                    .build()).toList();

            return OrderResponseDTO.builder()
                    .id(o.getId())
                    .buyerId(o.getBuyer().getId())
                    .buyerName(o.getBuyer().getFullName())
                    .sellerId(o.getSeller() != null ? o.getSeller().getId() : null)
                    .sellerName(o.getSeller() != null ? o.getSeller().getFullName() : null)
                    .totalAmount(o.getTotalAmount())
                    .commissionFee(o.getCommissionFee())
                    .status(o.getStatus())
                    .checkoutOrderId(order.getId())
                    .items(itemDTOs)
                    .createdAt(o.getCreatedAt())
                    .updatedAt(o.getUpdatedAt())
                    .build();
        }).toList();

        boolean allCustomQuoted = customs.isEmpty() || customs.stream().allMatch(c -> c.getQuotedPrice() != null);
        boolean readyForConfirm = "PENDING_QUOTE".equals(order.getStatus()) && allCustomQuoted;

        BigDecimal computedTotal = order.getTotalAmount();
        if (customs != null && !customs.isEmpty() && customs.stream().anyMatch(c -> c.getQuotedPrice() != null)) {
            BigDecimal marketSum = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal customSum = customs.stream()
                    .filter(c -> c.getQuotedPrice() != null && !"CANCELLED".equals(c.getStatus()))
                    .map(c -> {
                        int q = c.getQuantity() != null && c.getQuantity() > 0 ? c.getQuantity() : 1;
                        return (c.getPriceVersion() != null && c.getPriceVersion() >= 2)
                                ? c.getQuotedPrice().multiply(BigDecimal.valueOf(q))
                                : c.getQuotedPrice();
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ship = order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO;
            computedTotal = marketSum.add(customSum).add(ship);
        }

        return CheckoutOrderResponseDTO.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .shippingAddress(order.getShippingAddress())
                .shippingMode(order.getShippingMode())
                .status(order.getStatus())
                .totalAmount(computedTotal)
                .shippingFee(order.getShippingFee())
                .marketplaceShippingFee(order.getMarketplaceShippingFee())
                .customShippingFee(order.getCustomShippingFee())
                .shippingFeeAssignedScope(order.getShippingFeeAssignedScope())
                .marketplaceStatus(order.getMarketplaceStatus())
                .customStatus(order.getCustomStatus())
                .paymentMethod(order.getPaymentMethod())
                .summaryVersion(order.getSummaryVersion())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .customOrders(customDTOs)
                .marketplaceOrders(orderDTOs)
                .allCustomQuoted(allCustomQuoted)
                .readyForConfirm(readyForConfirm)
                .paymentLinkUrl(paymentLinkUrl)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutOrderResponseDTO getExistingByIdempotency(User user, String idempotencyKey) {
        return checkoutOrderRepository.findFirstByUserIdAndIdempotencyKey(user.getId(), idempotencyKey)
                .map(order -> toDTO(order, null))
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));
    }
}
