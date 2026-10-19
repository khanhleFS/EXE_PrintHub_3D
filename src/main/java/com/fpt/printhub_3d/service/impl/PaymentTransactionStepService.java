package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.PaymentAttemptStatusResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionStepService {

    private final OrderRepository orderRepository;
    private final CustomOrderRepository customOrderRepository;
    private final CheckoutOrderRepository checkoutOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCESS", "CANCELLED", "RECONCILIATION_REQUIRED", "DUPLICATE_PAID"
    );

    public record PrepareAttemptResult(
            boolean ready,
            boolean initializing,
            boolean needsVerification,
            UUID attemptId,
            Long providerCode,
            BigDecimal amount,
            String scope,
            String checkoutUrl,
            String qrCode,
            Instant expiresAt,
            Integer retryAfterSeconds,
            Payment payment
    ) {
        public static PrepareAttemptResult ready(PaymentAttempt attempt, Payment payment) {
            return new PrepareAttemptResult(
                    true, false, false, attempt.getId(), attempt.getProviderCode(),
                    attempt.getAmount(), attempt.getScope(), attempt.getCheckoutUrl(),
                    attempt.getQrCode(), attempt.getExpiresAt(), null, payment);
        }

        public static PrepareAttemptResult initializing(UUID attemptId, Long providerCode, BigDecimal amount, String scope, int retryAfterSeconds) {
            return new PrepareAttemptResult(
                    false, true, false, attemptId, providerCode, amount, scope, null, null, null, retryAfterSeconds, null);
        }

        public static PrepareAttemptResult needsVerification(UUID attemptId, Long providerCode, BigDecimal amount, String scope, Instant expiresAt) {
            return new PrepareAttemptResult(
                    false, false, true, attemptId, providerCode, amount, scope, null, null, expiresAt, 2, null);
        }

        public static PrepareAttemptResult claimed(UUID attemptId, Long providerCode, BigDecimal amount, String scope, Instant expiresAt, Payment payment) {
            return new PrepareAttemptResult(
                    false, false, false, attemptId, providerCode, amount, scope, null, null, expiresAt, null, payment);
        }
    }

    /**
     * Phase 1 (Tx 1): Khóa entity, xác thực nghiệp vụ, kiểm tra attempt hiện hành.
     * Nếu đang có luồng khác khởi tạo (< 30s): trả HTTP 202 (INITIALIZING).
     * Nếu link còn hạn: trả về link tái sử dụng.
     * Nếu chưa có hoặc đã hết hạn: tạo PaymentAttempt mới (status = INITIALIZING), commit Tx 1.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PrepareAttemptResult preparePaymentAttempt(UUID userId, CreatePaymentLinkRequestDTO request) {
        if (request.paymentOption() != null && !"FULL".equals(request.paymentOption())) {
            throw bad("Chỉ hỗ trợ thanh toán toàn bộ báo giá đã xác nhận");
        }

        Order order = null;
        CustomOrder custom = null;
        CheckoutOrder checkoutOrder = null;
        BigDecimal total;
        Payment payment;
        String scope = (request.scope() != null && !request.scope().isBlank()) ? request.scope().toUpperCase() : "ALL";

        if ("ORDER".equals(request.orderType())) {
            scope = "MARKETPLACE";
            order = orderRepository.findLockedById(request.orderId())
                    .orElseThrow(() -> bad("Không tìm thấy đơn hàng"));
            if (!order.getBuyer().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (order.getCheckoutOrder() != null) {
                throw bad("Đơn hàng này thuộc phiên thanh toán giỏ hàng " + order.getCheckoutOrder().getId() + ". Vui lòng thanh toán qua CheckoutOrder.");
            }
            if (!"PENDING".equals(order.getStatus())) {
                throw bad("Đơn không ở trạng thái chờ thanh toán");
            }
            total = order.getTotalAmount();
            payment = paymentRepository.findByOrderId(order.getId()).orElseGet(Payment::new);
            payment.setScope("MARKETPLACE");

        } else if ("CUSTOM_ORDER".equals(request.orderType())) {
            scope = "CUSTOM";
            custom = customOrderRepository.findLockedById(request.orderId())
                    .orElseThrow(() -> bad("Không tìm thấy yêu cầu in"));
            if (!custom.getBuyer().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (custom.getCheckoutOrder() != null) {
                throw bad("Yêu cầu in 3D này thuộc phiên thanh toán giỏ hàng " + custom.getCheckoutOrder().getId() + ". Vui lòng thanh toán qua CheckoutOrder.");
            }
            if (!"ACCEPTED".equals(custom.getStatus())) {
                throw bad("Yêu cầu in chưa được khách hàng chấp nhận báo giá để thanh toán");
            }
            if (custom.getQuotedPrice() == null || custom.getQuotedPrice().signum() <= 0) {
                throw bad("Yêu cầu in chưa có giá hợp lệ");
            }
            int q = custom.getQuantity() != null && custom.getQuantity() > 0 ? custom.getQuantity() : 1;
            total = (custom.getPriceVersion() != null && custom.getPriceVersion() >= 2)
                    ? custom.getQuotedPrice().multiply(BigDecimal.valueOf(q))
                    : custom.getQuotedPrice();
            payment = paymentRepository.findByCustomOrderId(custom.getId()).orElseGet(Payment::new);
            payment.setScope("CUSTOM");

        } else if ("CHECKOUT_ORDER".equals(request.orderType())) {
            checkoutOrder = checkoutOrderRepository.findLockedById(request.orderId())
                    .orElseThrow(() -> bad("Không tìm thấy đơn thanh toán"));
            if (!checkoutOrder.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }

            if (request.scope() != null && !request.scope().isBlank()) {
                scope = request.scope().toUpperCase();
            } else {
                scope = "ALL";
            }
            if ("SEPARATE".equalsIgnoreCase(checkoutOrder.getShippingMode())) {
                if (!"MARKETPLACE".equals(scope) && !"CUSTOM".equals(scope)) {
                    throw bad("Đơn hàng giao riêng từng đợt (SEPARATE) bắt buộc thanh toán theo nhóm: MARKETPLACE hoặc CUSTOM");
                }
                if ("MARKETPLACE".equals(scope)) {
                    if ("PAID".equalsIgnoreCase(checkoutOrder.getMarketplaceStatus())) {
                        throw bad("Nhóm sản phẩm có sẵn (Marketplace) đã được thanh toán");
                    }
                    if ("COD".equalsIgnoreCase(checkoutOrder.getPaymentMethod())) {
                        throw bad("Nhóm sản phẩm Marketplace được đặt theo hình thức COD (thanh toán khi nhận hàng)");
                    }
                    List<Order> childOrders = orderRepository.findByCheckoutOrderId(checkoutOrder.getId());
                    if (childOrders.isEmpty()) {
                        throw bad("Đơn hàng không có sản phẩm có sẵn (marketplace) để thanh toán");
                    }
                    BigDecimal marketSum = childOrders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal shipFee = "MARKETPLACE".equalsIgnoreCase(checkoutOrder.getShippingFeeAssignedScope())
                            ? (checkoutOrder.getMarketplaceShippingFee() != null ? checkoutOrder.getMarketplaceShippingFee() : (checkoutOrder.getShippingFee() != null ? checkoutOrder.getShippingFee() : BigDecimal.ZERO))
                            : BigDecimal.ZERO;
                    total = marketSum.add(shipFee);
                } else {
                    if ("PAID".equalsIgnoreCase(checkoutOrder.getCustomStatus())) {
                        throw bad("Nhóm sản phẩm in theo yêu cầu (Custom) đã được thanh toán");
                    }
                    List<CustomOrder> childCustoms = customOrderRepository.findByCheckoutOrderId(checkoutOrder.getId());
                    if (childCustoms.isEmpty()) {
                        throw bad("Đơn hàng không có sản phẩm in 3D (custom) để thanh toán");
                    }
                    boolean allAccepted = childCustoms.stream().allMatch(c -> "ACCEPTED".equals(c.getStatus()) || "PAID".equals(c.getStatus()) || "CANCELLED".equals(c.getStatus()));
                    if (!allAccepted) {
                        throw bad("Các sản phẩm in 3D theo yêu cầu chưa được xác nhận báo giá (vui lòng xác nhận trước khi thanh toán)");
                    }
                    BigDecimal customSum = childCustoms.stream()
                            .filter(c -> !"CANCELLED".equals(c.getStatus()) && c.getQuotedPrice() != null)
                            .map(c -> {
                                int q = c.getQuantity() != null && c.getQuantity() > 0 ? c.getQuantity() : 1;
                                return (c.getPriceVersion() != null && c.getPriceVersion() >= 2)
                                        ? c.getQuotedPrice().multiply(BigDecimal.valueOf(q))
                                        : c.getQuotedPrice();
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal shipFee = "CUSTOM".equalsIgnoreCase(checkoutOrder.getShippingFeeAssignedScope())
                            ? (checkoutOrder.getCustomShippingFee() != null ? checkoutOrder.getCustomShippingFee() : BigDecimal.ZERO)
                            : BigDecimal.ZERO;
                    total = customSum.add(shipFee);
                }
            } else {
                if (!"ALL".equals(scope)) {
                    throw bad("Đơn hàng giao chung (COMMON) yêu cầu thanh toán toàn bộ (scope = ALL)");
                }
                if (!"CONFIRMED".equals(checkoutOrder.getStatus())) {
                    throw bad("Đơn không ở trạng thái có thể thanh toán (yêu cầu trạng thái CONFIRMED)");
                }
                total = checkoutOrder.getTotalAmount();
            }

            payment = paymentRepository.findByCheckoutOrderIdAndScope(checkoutOrder.getId(), scope).orElseGet(Payment::new);
            payment.setScope(scope);

        } else {
            throw bad("Loại đơn hàng không hợp lệ");
        }

        if (total == null || total.signum() <= 0) {
            throw bad("Đơn chưa có số tiền thanh toán hợp lệ");
        }
        if (request.customAmount() != null && request.customAmount().compareTo(total) != 0) {
            throw bad("Số tiền phải khớp giá từ hệ thống");
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            throw bad("Đơn đã thanh toán");
        }
        if ("COD".equals(payment.getGateway())) {
            throw bad("Đơn đã chọn thanh toán khi nhận hàng");
        }

        // Kiểm tra attempt gần nhất
        Optional<PaymentAttempt> latestAttempt = (checkoutOrder != null)
                ? paymentAttemptRepository.findFirstByCheckoutOrderIdAndScopeOrderByCreatedAtDesc(checkoutOrder.getId(), scope)
                : (order != null ? paymentAttemptRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                : (custom != null ? paymentAttemptRepository.findFirstByCustomOrderIdOrderByCreatedAtDesc(custom.getId()) : Optional.empty()));

        Instant now = Instant.now();
        if (latestAttempt.isPresent()) {
            PaymentAttempt existing = latestAttempt.get();
            if ("SUCCESS".equals(existing.getStatus())) {
                throw bad("Giao dịch đã thanh toán thành công");
            }

            // Case A: Đang có luồng khác khởi tạo gần đây (< 30s) mà chưa có link
            if (("INITIALIZING".equals(existing.getStatus()) || "PENDING".equals(existing.getStatus()) || "PENDING_VERIFY".equals(existing.getStatus()))
                    && existing.getCheckoutUrl() == null
                    && existing.getCreatedAt().isAfter(now.minusSeconds(30))) {
                log.info("R2-05: Đang có request khác khởi tạo liên kết cho attemptId={}, trả về HTTP 202 Polling", existing.getId());
                return PrepareAttemptResult.initializing(existing.getId(), existing.getProviderCode(), existing.getAmount(), existing.getScope(), 2);
            }

            // Case B: Link đã sẵn sàng và còn hạn
            boolean isNotExpired = existing.getExpiresAt() != null && existing.getExpiresAt().isAfter(now);
            if (isNotExpired && existing.getCheckoutUrl() != null && !"CANCELLED".equals(existing.getStatus())) {
                log.info("R2-05: Tái sử dụng link thanh toán còn hạn từ attemptId={}", existing.getId());
                payment.setTransactionId(String.valueOf(existing.getProviderCode()));
                payment.setCheckoutUrl(existing.getCheckoutUrl());
                payment.setAmount(existing.getAmount());
                payment.setExpiresAt(existing.getExpiresAt());
                payment.setStatus("PENDING");
                paymentRepository.save(payment);
                return PrepareAttemptResult.ready(existing, payment);
            }

            // Case C: Attempt cũ có providerCode nhưng chưa có URL, hoặc PENDING_VERIFY:
            // Phải xác minh mã cũ trên cổng PayOS trước khi quyết định, tránh tạo 2 link song song
            if (existing.getProviderCode() != null && ("INITIALIZING".equals(existing.getStatus())
                    || "PENDING".equals(existing.getStatus())
                    || "PENDING_VERIFY".equals(existing.getStatus()))) {
                log.info("R2-05: Attempt cũ id={} providerCode={} cần xác minh trạng thái trên PayOS trước khi cho tạo mã mới",
                        existing.getId(), existing.getProviderCode());
                return PrepareAttemptResult.needsVerification(existing.getId(), existing.getProviderCode(), existing.getAmount(), existing.getScope(), existing.getExpiresAt());
            }

            // Case D: Attempt cũ đã chốt hủy / hết hạn thực sự
            if ("INITIALIZING".equals(existing.getStatus()) || "PENDING".equals(existing.getStatus())) {
                existing.setStatus("EXPIRED");
                paymentAttemptRepository.save(existing);
            }
        }

        // Tạo attempt mới
        long code = UUID.randomUUID().getMostSignificantBits() & 0x1FFFFFFFFFFFFFL;
        if (code <= 0) {
            code = System.currentTimeMillis();
        }
        while (paymentRepository.findByTransactionId(String.valueOf(code)).isPresent()
                || paymentAttemptRepository.findByProviderCode(code).isPresent()) {
            code = (UUID.randomUUID().getMostSignificantBits() & 0x1FFFFFFFFFFFFFL) + 1;
        }

        Instant expiry = now.plusSeconds(900);
        Instant resExpiry = expiry.plus(java.time.Duration.ofMinutes(5));

        // Gia hạn giữ kho khả dụng TRƯỚC KHI tạo link
        if (checkoutOrder != null) {
            String attemptScope = scope != null ? scope : "ALL";
            if ("ALL".equals(attemptScope) || "MARKETPLACE".equals(attemptScope)) {
                List<InventoryReservation> resList = inventoryReservationRepository.findByCheckoutOrderId(checkoutOrder.getId());
                for (var r : resList) {
                    if ("RESERVED".equals(r.getStatus())) {
                        if (r.getExpiresAt().isBefore(now)) {
                            Product p = productRepository.findLockedById(r.getProduct().getId()).orElse(null);
                            if (p != null) {
                                Integer activeReserved = inventoryReservationRepository.getActiveReservedQuantity(p.getId(), now);
                                int available = p.getStock() - (activeReserved != null ? activeReserved : 0);
                                if (available < r.getQuantity()) {
                                    throw bad("Sản phẩm '" + p.getTitle() + "' đã hết hạn giữ kho và hiện không còn đủ số lượng tồn");
                                }
                            }
                        }
                        r.setExpiresAt(resExpiry);
                        r.setUpdatedAt(now);
                        inventoryReservationRepository.save(r);
                    }
                }
            }
            if (!"SEPARATE".equalsIgnoreCase(checkoutOrder.getShippingMode()) || "MARKETPLACE".equals(scope)) {
                checkoutOrder.setPaymentMethod("PAYOS");
            }
            checkoutOrder.setPaymentExpiresAt(expiry);
            checkoutOrder.setReservationExpiresAt(resExpiry);
            checkoutOrderRepository.save(checkoutOrder);
        }

        if (custom != null) {
            custom.setPaymentMethod("PAYOS");
        }

        // Lưu Payment và PaymentAttempt
        payment.setOrder(order);
        payment.setCustomOrder(custom);
        payment.setCheckoutOrder(checkoutOrder);
        payment.setGateway("PAYOS");
        payment.setStatus("PENDING");
        payment.setTransactionId(String.valueOf(code));
        payment.setAmount(total);
        payment.setExpiresAt(expiry);
        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(now);
        }
        payment.setUpdatedAt(now);
        payment = paymentRepository.saveAndFlush(payment);

        // Chuẩn hóa priceVersion theo scope độc lập (R2-06 / P1.2)
        Long priceVersion;
        String attemptScope = scope != null ? scope : "ALL";
        if ("MARKETPLACE".equalsIgnoreCase(attemptScope)) {
            priceVersion = 1L; // Hàng có sẵn niêm yết cố định không phụ thuộc báo giá custom
        } else if ("CUSTOM".equalsIgnoreCase(attemptScope)) {
            if (checkoutOrder != null) {
                priceVersion = checkoutOrder.getCustomPricingVersion() != null ? checkoutOrder.getCustomPricingVersion() : 1L;
            } else if (custom != null) {
                priceVersion = custom.getQuoteVersion() != null ? custom.getQuoteVersion()
                        : (custom.getPriceVersion() != null ? custom.getPriceVersion().longValue() : 1L);
            } else {
                priceVersion = 1L;
            }
        } else { // ALL
            priceVersion = checkoutOrder != null ? (checkoutOrder.getSummaryVersion() != null ? checkoutOrder.getSummaryVersion() : 1L) : 1L;
        }

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setCheckoutOrder(checkoutOrder);
        attempt.setOrder(order);
        attempt.setCustomOrder(custom);
        attempt.setProviderCode(code);
        attempt.setAmount(total);
        attempt.setScope(scope);
        attempt.setStatus("INITIALIZING");
        attempt.setPriceVersion(priceVersion != null ? priceVersion : 1L);
        attempt.setCreatedAt(now);
        attempt.setExpiresAt(expiry);
        attempt = paymentAttemptRepository.saveAndFlush(attempt);

        log.info("R2-05 Phase 1: Đã claim và commit PaymentAttempt ID={} providerCode={} scope={} priceVersion={} status=INITIALIZING",
                attempt.getId(), code, scope, priceVersion);

        return PrepareAttemptResult.claimed(attempt.getId(), code, total, scope, expiry, payment);
    }

    /**
     * Phase 3 (Tx 2): Cập nhật kết quả nhận được từ PayOS.
     * TUYỆT ĐỐI KHÔNG ghi đè nếu attempt hoặc payment đã ở trạng thái kết thúc (SUCCESS, CANCELLED, ...).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizePaymentAttempt(UUID attemptId, Long providerCode, String checkoutUrl, String qrCode) {
        PaymentAttempt attempt = paymentAttemptRepository.findLockedById(attemptId)
                .orElseGet(() -> paymentAttemptRepository.findLockedByProviderCode(providerCode).orElse(null));

        if (attempt == null) {
            log.error("R2-05 Phase 3: Không tìm thấy PaymentAttempt ID={} providerCode={}", attemptId, providerCode);
            return;
        }

        if (TERMINAL_STATUSES.contains(attempt.getStatus())) {
            log.warn("R2-05 Phase 3: PaymentAttempt ID={} đã ở trạng thái kết thúc [{}]. Giữ nguyên trạng thái, chỉ bổ sung URL nếu trống.",
                    attempt.getId(), attempt.getStatus());
            if (attempt.getCheckoutUrl() == null) {
                attempt.setCheckoutUrl(checkoutUrl);
                attempt.setQrCode(qrCode);
                paymentAttemptRepository.save(attempt);
            }
            return;
        }

        attempt.setStatus("READY");
        attempt.setCheckoutUrl(checkoutUrl);
        attempt.setQrCode(qrCode);
        paymentAttemptRepository.save(attempt);

        Payment payment = attempt.getPayment();
        if (payment != null && !TERMINAL_STATUSES.contains(payment.getStatus())) {
            payment.setCheckoutUrl(checkoutUrl);
            payment.setStatus("PENDING");
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
        }

        log.info("R2-05 Phase 3: Hoàn tất kích hoạt liên kết thanh toán cho attemptId={}, providerCode={}",
                attempt.getId(), providerCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttemptTimeout(Long providerCode, String failureReason) {
        paymentAttemptRepository.findLockedByProviderCode(providerCode).ifPresent(attempt -> {
            if (!TERMINAL_STATUSES.contains(attempt.getStatus())) {
                attempt.setStatus("PENDING_VERIFY");
                paymentAttemptRepository.save(attempt);
                log.warn("R2-05: Chuyển PaymentAttempt providerCode={} sang PENDING_VERIFY: {}", providerCode, failureReason);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttemptFailed(Long providerCode, String failureReason) {
        paymentAttemptRepository.findLockedByProviderCode(providerCode).ifPresent(attempt -> {
            if (!TERMINAL_STATUSES.contains(attempt.getStatus())) {
                attempt.setStatus("FAILED");
                paymentAttemptRepository.save(attempt);
                log.warn("R2-05: Đánh dấu PaymentAttempt providerCode={} thành FAILED: {}", providerCode, failureReason);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAttemptExpired(UUID attemptId) {
        paymentAttemptRepository.findById(attemptId).ifPresent(attempt -> {
            if (!TERMINAL_STATUSES.contains(attempt.getStatus())) {
                attempt.setStatus("EXPIRED");
                paymentAttemptRepository.save(attempt);
                log.info("R2-05: Đã chuyển attemptId={} sang EXPIRED sau khi PayOS xác nhận cổng không còn link", attemptId);
            }
        });
    }

    @Transactional(readOnly = true)
    public PaymentAttemptStatusResponseDTO getAttemptStatus(UUID attemptId, UUID userId) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thanh toán"));

        UUID ownerId = null;
        if (attempt.getCheckoutOrder() != null) {
            ownerId = attempt.getCheckoutOrder().getUser().getId();
        } else if (attempt.getOrder() != null) {
            ownerId = attempt.getOrder().getBuyer().getId();
        } else if (attempt.getCustomOrder() != null) {
            ownerId = attempt.getCustomOrder().getBuyer().getId();
        } else if (attempt.getPayment() != null) {
            Payment p = attempt.getPayment();
            if (p.getCheckoutOrder() != null) ownerId = p.getCheckoutOrder().getUser().getId();
            else if (p.getOrder() != null) ownerId = p.getOrder().getBuyer().getId();
            else if (p.getCustomOrder() != null) ownerId = p.getCustomOrder().getBuyer().getId();
        }

        if (ownerId == null || !ownerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem thông tin phiên thanh toán này");
        }

        String msg;
        Integer retryAfter = null;
        switch (attempt.getStatus()) {
            case "INITIALIZING" -> {
                msg = "Đang khởi tạo liên kết thanh toán PayOS...";
                retryAfter = 2;
            }
            case "READY" -> msg = "Liên kết thanh toán đã sẵn sàng";
            case "SUCCESS" -> msg = "Giao dịch đã được thanh toán thành công";
            case "CANCELLED" -> msg = "Giao dịch đã bị hủy";
            case "EXPIRED" -> msg = "Liên kết thanh toán đã hết hạn";
            case "PENDING_VERIFY" -> msg = "Giao dịch đang chờ xác minh từ cổng thanh toán";
            case "RECONCILIATION_REQUIRED" -> msg = "Giao dịch cần đối soát hoàn tiền";
            case "FAILED" -> msg = "Khởi tạo liên kết thanh toán thất bại";
            default -> msg = attempt.getStatus();
        }

        return PaymentAttemptStatusResponseDTO.builder()
                .attemptId(attempt.getId())
                .providerCode(attempt.getProviderCode())
                .status(attempt.getStatus())
                .scope(attempt.getScope())
                .amount(attempt.getAmount())
                .checkoutUrl(attempt.getCheckoutUrl())
                .qrCode(attempt.getQrCode())
                .expiresAt(attempt.getExpiresAt())
                .retryAfterSeconds(retryAfter)
                .message(msg)
                .build();
    }

    public Payment lockPayment(String code) {
        Payment payment = paymentRepository.findByTransactionId(code).orElseGet(() -> {
            try {
                return paymentAttemptRepository.findByProviderCode(Long.valueOf(code))
                        .map(PaymentAttempt::getPayment)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch");
            }
        });

        if (payment.getOrder() != null) {
            orderRepository.findLockedById(payment.getOrder().getId()).orElseThrow();
        } else if (payment.getCustomOrder() != null) {
            customOrderRepository.findLockedById(payment.getCustomOrder().getId()).orElseThrow();
        } else if (payment.getCheckoutOrder() != null) {
            checkoutOrderRepository.findLockedById(payment.getCheckoutOrder().getId()).orElseThrow();
        }

        return payment;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment settlePayment(Long providerCode, BigDecimal amount) {
        Payment payment = lockPayment(String.valueOf(providerCode));
        settle(payment, amount, providerCode);
        return payment;
    }

    @Transactional
    public void settle(Payment payment, BigDecimal amount, Long providerCode) {
        PaymentAttempt attempt = null;
        if (providerCode != null) {
            attempt = paymentAttemptRepository.findByProviderCode(providerCode).orElse(null);
        }
        BigDecimal expectedAmount = (attempt != null && attempt.getAmount() != null)
                ? attempt.getAmount()
                : payment.getAmount();

        if (amount == null || amount.compareTo(expectedAmount) != 0) {
            throw bad("Số tiền giao dịch không khớp đơn hàng (nhận được: " + amount + ", kỳ vọng: " + expectedAmount + ")");
        }

        if ("SUCCESS".equals(payment.getStatus()) && (attempt == null || "SUCCESS".equals(attempt.getStatus()))) {
            return;
        }

        User buyer = (payment.getOrder() != null) ? payment.getOrder().getBuyer()
                : (payment.getCustomOrder() != null ? payment.getCustomOrder().getBuyer()
                : (payment.getCheckoutOrder() != null ? payment.getCheckoutOrder().getUser() : null));

        // 1. Kiểm tra đơn hoặc đơn con bị hủy (Task 1.5)
        boolean hasCancelledChild = false;
        if (payment.getOrder() != null) {
            if ("CANCELLED".equals(payment.getOrder().getStatus())) {
                hasCancelledChild = true;
            }
        } else if (payment.getCustomOrder() != null) {
            if ("CANCELLED".equals(payment.getCustomOrder().getStatus())) {
                hasCancelledChild = true;
            }
        } else if (payment.getCheckoutOrder() != null) {
            CheckoutOrder co = payment.getCheckoutOrder();
            if ("CANCELLED".equals(co.getStatus())) {
                hasCancelledChild = true;
            } else {
                String scope = attempt != null ? attempt.getScope() : (payment.getScope() != null ? payment.getScope() : "ALL");
                if ("ALL".equals(scope) || "MARKETPLACE".equals(scope)) {
                    List<Order> childOrders = orderRepository.findByCheckoutOrderId(co.getId());
                    if (childOrders.stream().anyMatch(o -> "CANCELLED".equals(o.getStatus()))) {
                        hasCancelledChild = true;
                    }
                }
                if ("ALL".equals(scope) || "CUSTOM".equals(scope)) {
                    List<CustomOrder> childCustoms = customOrderRepository.findByCheckoutOrderId(co.getId());
                    if (childCustoms.stream().anyMatch(c -> "CANCELLED".equals(c.getStatus()))) {
                        hasCancelledChild = true;
                    }
                }
            }
        }

        if (hasCancelledChild) {
            log.error("CẢNH BÁO: Nhận thanh toán cho đơn có đơn con đã CANCELLED. Đánh dấu RECONCILIATION_REQUIRED.");
            payment.setStatus("RECONCILIATION_REQUIRED");
            payment.setPaidAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            if (attempt != null) {
                attempt.setStatus("RECONCILIATION_REQUIRED");
                attempt.setPaidAt(Instant.now());
                paymentAttemptRepository.save(attempt);
            }
            if (payment.getCheckoutOrder() != null) {
                CheckoutOrder co = payment.getCheckoutOrder();
                co.setStatus("RECONCILIATION_REQUIRED");
                co.setUpdatedAt(Instant.now());
                checkoutOrderRepository.save(co);
            }
            paymentRepository.save(payment);
            if (buyer != null) {
                notificationService.sendNotification(buyer, "Cần đối soát hoàn tiền",
                        "Đơn hàng chứa sản phẩm đã bị hủy trước khi nhận thanh toán. Giao dịch " + (providerCode != null ? providerCode : payment.getTransactionId()) + " cần đối soát hoàn tiền.",
                        "PAYMENT", "/orders");
            }
            return;
        }

        // 2. Đối chiếu phiên bản giá (priceVersion) theo scope chuẩn hóa (Task 1.6 / R2-06 / P1.2)
        Long currentVersion = null;
        String attemptScope = (attempt != null && attempt.getScope() != null) ? attempt.getScope().toUpperCase() : "ALL";
        if ("MARKETPLACE".equals(attemptScope)) {
            currentVersion = 1L; // Hàng có sẵn niêm yết cố định không phụ thuộc báo giá custom
        } else if ("CUSTOM".equals(attemptScope)) {
            if (payment.getCheckoutOrder() != null) {
                currentVersion = payment.getCheckoutOrder().getCustomPricingVersion() != null
                        ? payment.getCheckoutOrder().getCustomPricingVersion() : 1L;
            } else if (payment.getCustomOrder() != null) {
                currentVersion = payment.getCustomOrder().getQuoteVersion() != null
                        ? payment.getCustomOrder().getQuoteVersion()
                        : (payment.getCustomOrder().getPriceVersion() != null ? payment.getCustomOrder().getPriceVersion().longValue() : 1L);
            } else {
                currentVersion = 1L;
            }
        } else { // ALL
            if (payment.getCheckoutOrder() != null) {
                currentVersion = payment.getCheckoutOrder().getSummaryVersion() != null
                        ? payment.getCheckoutOrder().getSummaryVersion() : 1L;
            } else if (payment.getCustomOrder() != null) {
                currentVersion = payment.getCustomOrder().getQuoteVersion() != null
                        ? payment.getCustomOrder().getQuoteVersion()
                        : (payment.getCustomOrder().getPriceVersion() != null ? payment.getCustomOrder().getPriceVersion().longValue() : 1L);
            } else {
                currentVersion = 1L;
            }
        }

        if (attempt != null && attempt.getPriceVersion() != null && currentVersion != null && attempt.getPriceVersion() < currentVersion) {
            log.error("CẢNH BÁO R2-06 P1.2: Attempt priceVersion [{}] nhỏ hơn phiên bản hiện hành của đơn [{}] (scope: {}). Đánh dấu RECONCILIATION_REQUIRED.",
                    attempt.getPriceVersion(), currentVersion, attemptScope);
            payment.setStatus("RECONCILIATION_REQUIRED");
            payment.setPaidAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            attempt.setStatus("RECONCILIATION_REQUIRED");
            attempt.setPaidAt(Instant.now());
            paymentAttemptRepository.save(attempt);
            if (payment.getCheckoutOrder() != null) {
                CheckoutOrder co = payment.getCheckoutOrder();
                co.setStatus("RECONCILIATION_REQUIRED");
                co.setUpdatedAt(Instant.now());
                checkoutOrderRepository.save(co);
            }
            paymentRepository.save(payment);
            if (buyer != null) {
                notificationService.sendNotification(buyer, "Cần đối soát báo giá",
                        "Giao dịch " + (providerCode != null ? providerCode : payment.getTransactionId()) + " thanh toán theo báo giá phiên bản cũ. Hệ thống đã đưa vào đối soát.",
                        "PAYMENT", "/orders");
            }
            return;
        }

        if (payment.getOrder() != null) {
            Order order = payment.getOrder();
            order.setStatus("PAID");
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            payment.setStatus("SUCCESS");
            payment.setPaidAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            if (attempt != null) {
                attempt.setStatus("SUCCESS");
                attempt.setPaidAt(Instant.now());
                paymentAttemptRepository.save(attempt);
            }
        } else if (payment.getCustomOrder() != null) {
            CustomOrder customOrder = payment.getCustomOrder();
            customOrder.setStatus("PAID");
            customOrder.setUpdatedAt(Instant.now());
            customOrderRepository.save(customOrder);
            payment.setStatus("SUCCESS");
            payment.setPaidAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            if (attempt != null) {
                attempt.setStatus("SUCCESS");
                attempt.setPaidAt(Instant.now());
                paymentAttemptRepository.save(attempt);
            }
        } else if (payment.getCheckoutOrder() != null) {
            CheckoutOrder checkoutOrder = payment.getCheckoutOrder();
            String scope = attempt != null ? attempt.getScope() : (payment.getScope() != null ? payment.getScope() : "ALL");

            // Idempotent & Duplicate Paid Guard (R06)
            boolean alreadyPaidScope = false;
            if ("MARKETPLACE".equals(scope) && "PAID".equalsIgnoreCase(checkoutOrder.getMarketplaceStatus())) {
                alreadyPaidScope = true;
            } else if ("CUSTOM".equals(scope) && "PAID".equalsIgnoreCase(checkoutOrder.getCustomStatus())) {
                alreadyPaidScope = true;
            } else if ("ALL".equals(scope) && ("PAID".equalsIgnoreCase(checkoutOrder.getMarketplaceStatus()) || "PAID".equalsIgnoreCase(checkoutOrder.getCustomStatus()) || "SUCCESS".equalsIgnoreCase(payment.getStatus()))) {
                alreadyPaidScope = true;
            }

            if (alreadyPaidScope) {
                log.warn("CẢNH BÁO R06: Nhận tiền thanh toán lặp lại cho checkoutOrder [{}] scope [{}]. Đánh dấu DUPLICATE_PAID để đối soát.", checkoutOrder.getId(), scope);
                if (attempt != null) {
                    attempt.setStatus("DUPLICATE_PAID");
                    attempt.setPaidAt(Instant.now());
                    paymentAttemptRepository.save(attempt);
                }
                notificationService.sendNotification(buyer, "Thanh toán lặp lại", "Giao dịch " + (providerCode != null ? providerCode : payment.getTransactionId()) + " được ghi nhận là thanh toán lặp. Vui lòng liên hệ hỗ trợ.", "PAYMENT", "/orders");
                return;
            }

            boolean hasOversold = false;
            List<Order> childOrders = orderRepository.findByCheckoutOrderId(checkoutOrder.getId());

            if ("ALL".equals(scope) || "MARKETPLACE".equals(scope)) {
                List<InventoryReservation> reservations = inventoryReservationRepository.findByCheckoutOrderId(checkoutOrder.getId());
                Instant now = Instant.now();
                for (var res : reservations) {
                    if ("RESERVED".equals(res.getStatus())) {
                        Product prod = productRepository.findLockedById(res.getProduct().getId()).orElse(null);
                        if (prod != null) {
                            boolean wasExpired = res.getExpiresAt().isBefore(now);
                            int needed = res.getQuantity();
                            boolean stockOk = false;
                            if (!wasExpired) {
                                stockOk = prod.getStock() >= needed;
                            } else {
                                Integer activeReserved = inventoryReservationRepository.getActiveReservedQuantity(prod.getId(), now);
                                int otherReserved = activeReserved != null ? activeReserved : 0;
                                int available = prod.getStock() - otherReserved;
                                stockOk = available >= needed && prod.getStock() >= needed;
                            }

                            if (!stockOk) {
                                hasOversold = true;
                                log.error("CẢNH BÁO L01/R05/R2-03: Sản phẩm [{}] tồn kho còn {} nhưng cần commit {}. Ghi nhận RECONCILIATION_REQUIRED.",
                                        prod.getTitle(), prod.getStock(), res.getQuantity());
                                res.setStatus("RECONCILIATION_REQUIRED");
                            } else {
                                prod.setStock(prod.getStock() - needed);
                                productRepository.save(prod);
                                res.setStatus("COMMITTED");
                            }
                            res.setUpdatedAt(now);
                            inventoryReservationRepository.save(res);
                        }
                    }
                }

                if (hasOversold) {
                    checkoutOrder.setMarketplaceStatus("RECONCILIATION_REQUIRED");
                    for (Order o : childOrders) {
                        o.setStatus("RECONCILIATION_REQUIRED");
                        o.setUpdatedAt(Instant.now());
                        orderRepository.save(o);
                    }
                } else {
                    checkoutOrder.setMarketplaceStatus("PAID");
                    for (Order o : childOrders) {
                        if (!"CANCELLED".equals(o.getStatus())) {
                            o.setStatus("PAID");
                            o.setUpdatedAt(Instant.now());
                            orderRepository.save(o);
                        }
                    }
                }
            }

            if ("ALL".equals(scope) || "CUSTOM".equals(scope)) {
                List<CustomOrder> childCustoms = customOrderRepository.findByCheckoutOrderId(checkoutOrder.getId());
                for (CustomOrder c : childCustoms) {
                    if (!"CANCELLED".equals(c.getStatus())) {
                        c.setStatus("PAID");
                        c.setUpdatedAt(Instant.now());
                        customOrderRepository.save(c);
                    }
                }
                checkoutOrder.setCustomStatus("PAID");
            }

            if (hasOversold) {
                payment.setStatus("RECONCILIATION_REQUIRED");
                checkoutOrder.setStatus("RECONCILIATION_REQUIRED");
                if (attempt != null) {
                    attempt.setStatus("RECONCILIATION_REQUIRED");
                    attempt.setPaidAt(Instant.now());
                    paymentAttemptRepository.save(attempt);
                }
            } else {
                payment.setStatus("SUCCESS");
                payment.setPaidAt(Instant.now());
                if (attempt != null) {
                    attempt.setStatus("SUCCESS");
                    attempt.setPaidAt(Instant.now());
                    paymentAttemptRepository.save(attempt);
                }
                if ("SEPARATE".equalsIgnoreCase(checkoutOrder.getShippingMode())) {
                    if ("PAID".equals(checkoutOrder.getMarketplaceStatus()) && "PAID".equals(checkoutOrder.getCustomStatus())) {
                        checkoutOrder.setStatus("PROCESSING");
                    } else {
                        checkoutOrder.setStatus("PARTIALLY_PAID");
                    }
                } else {
                    checkoutOrder.setStatus("PROCESSING");
                }
            }
            payment.setUpdatedAt(Instant.now());
            checkoutOrder.setUpdatedAt(Instant.now());
            checkoutOrderRepository.save(checkoutOrder);
        } else {
            throw bad("Giao dịch không có thông tin đơn hàng");
        }

        paymentRepository.save(payment);
        notificationService.sendNotification(
                buyer,
                "Đã nhận thanh toán",
                "Giao dịch " + (providerCode != null ? providerCode : payment.getTransactionId()) + " đã thanh toán thành công.",
                "PAYMENT",
                "/orders"
        );
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
