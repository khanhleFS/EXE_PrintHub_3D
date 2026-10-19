package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.dto.order.*;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
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
    private final InventoryReservationRepository inventoryReservationRepository;
    private final CheckoutOrderRepository checkoutOrderRepository;
    private final CustomOrderRepository customOrderRepository;
    private final CheckoutRewardService checkoutRewardService;

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");
    private static final BigDecimal REWARD_POINT_UNIT = new BigDecimal("10000");

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

        List<UUID> sortedProductIds = new ArrayList<>(totalRequestedStock.keySet());
        sortedProductIds.sort(UUID::compareTo);
        Instant now = Instant.now();

        for (UUID prodId : sortedProductIds) {
            Product p = productRepository.findLockedById(prodId)
                    .orElseThrow(() -> new ApiException(OrderErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm"));
            Integer activeReserved = inventoryReservationRepository.getActiveReservedQuantity(prodId, now);
            int available = p.getStock() - (activeReserved != null ? activeReserved : 0);
            int requested = totalRequestedStock.get(prodId);
            if (available < requested) {
                throw new ApiException(OrderErrorCode.OUT_OF_STOCK,
                        "Sản phẩm '" + p.getTitle() + "' không đủ tồn kho khả dụng (khả dụng: " + available + ", yêu cầu: " + requested + ")");
            }
            p.setStock(p.getStock() - requested);
            productRepository.save(p);
        }

        List<OrderResponseDTO> createdOrders = new ArrayList<>();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ItemWithProduct iwp : validatedItems) {
            BigDecimal itemTotal = iwp.product().getPrice().multiply(BigDecimal.valueOf(iwp.item().quantity()));
            totalAmount = totalAmount.add(itemTotal);
        }

        Order order = new Order();
        order.setBuyer(buyer);
        order.setSeller(null);
        order.setTotalAmount(totalAmount);
        order.setCommissionFee(BigDecimal.ZERO);
        order.setStatus("PENDING");
        order.setRewardProcessed(false);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        Order savedOrder = orderRepository.save(order);

        ShippingInfo shippingInfo = new ShippingInfo();
        shippingInfo.setOrders(savedOrder);
        shippingInfo.setRecipientName(request.recipientName());
        shippingInfo.setPhone(request.phone());
        shippingInfo.setAddress(request.address());
        shippingInfo.setProvince(request.province());
        shippingInfo.setTrackingNumber(null);
        shippingInfoRepository.save(shippingInfo);

        List<OrderItemResponseDTO> itemResponses = new ArrayList<>();
        for (ItemWithProduct iwp : validatedItems) {
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

        createdOrders.add(OrderResponseDTO.builder()
                .id(savedOrder.getId())
                .buyerId(buyer.getId())
                .buyerName(buyer.getFullName())
                .sellerId(null)
                .sellerName(null)
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
                .build());

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

        // P1.7: Chỉ cộng điểm khi ĐÃ THU tiền (payment.status == SUCCESS) VÀ đơn đã COMPLETED
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .or(() -> order.getCheckoutOrder() != null ? paymentRepository.findByCheckoutOrderIdAndScope(order.getCheckoutOrder().getId(), "MARKETPLACE") : Optional.empty())
                .or(() -> order.getCheckoutOrder() != null ? paymentRepository.findByCheckoutOrderIdAndScope(order.getCheckoutOrder().getId(), "ALL") : Optional.empty())
                .orElse(null);

        boolean isPaid = (payment != null && "SUCCESS".equals(payment.getStatus()))
                || (order.getCheckoutOrder() != null && "PAID".equalsIgnoreCase(order.getCheckoutOrder().getMarketplaceStatus()));

        if (!isPaid) {
            log.info("Chưa cấp điểm thưởng cho đơn {} vì tiền thanh toán chưa hoàn tất", order.getId());
            return RewardCompletionResponseDTO.builder()
                    .orderId(order.getId())
                    .customerId(buyer.getId())
                    .customerName(buyer.getFullName())
                    .rewardPointsEarned(0)
                    .totalRewardPoints(buyer.getRewardPoints() != null ? buyer.getRewardPoints() : 0)
                    .alreadyProcessed(false)
                    .build();
        }

        int earnedPoints = 0;
        CheckoutOrder co = order.getCheckoutOrder();
        if (co != null) {
            // P1.6: Dùng service tích điểm lũy kế tập trung, đồng bộ điểm đã cấp cho CheckoutOrder
            earnedPoints = checkoutRewardService.rewardCheckoutOrderCumulative(co.getId(), buyer);
        } else {
            BigDecimal eligibleAmount = BigDecimal.ZERO;
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            if (items != null && !items.isEmpty()) {
                for (OrderItem oi : items) {
                    if (oi.getUnitPrice() != null && oi.getQuantity() != null) {
                        eligibleAmount = eligibleAmount.add(oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())));
                    }
                }
            } else if (order.getTotalAmount() != null) {
                eligibleAmount = order.getTotalAmount();
            }
            earnedPoints = eligibleAmount.divideToIntegralValue(REWARD_POINT_UNIT).intValue();
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

        if (earnedPoints > 0) {
            wallet.setBalance(wallet.getBalance() + earnedPoints);
            wallet.setUpdatedAt(java.time.LocalDateTime.now());
            wallet = pointWalletRepository.save(wallet);
            buyer.setPointWallet(wallet);

            pointTransactionRepository.save(PointTransaction.builder()
                    .pointWallet(wallet).amount(earnedPoints)
                    .type(PointTransactionType.EARN)
                    .description("Hoàn thành đơn " + order.getId() + (co != null ? " (CheckoutOrder #" + co.getId() + ")" : ""))
                    .createdAt(java.time.LocalDateTime.now()).build());
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
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);

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

        List<Review> reviews = reviewRepository.findByOrderId(order.getId());
        Map<Long, Review> reviewByItem = reviews.stream()
                .filter(r -> r.getOrderItem() != null)
                .collect(Collectors.toMap(r -> r.getOrderItem().getId(), r -> r, (r1, r2) -> r1));

        List<OrderItemResponseDTO> itemDTOs = items != null ? items.stream()
                .map(item -> {
                    Review itemReview = reviewByItem.get(item.getId());
                    return OrderItemResponseDTO.builder()
                            .id(item.getId())
                            .productId(item.getProduct().getId())
                            .productTitle(item.getProduct().getTitle())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .subTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .color(item.getColor())
                            .engravingText(item.getEngravingText())
                            .reviewId(itemReview != null ? itemReview.getId() : null)
                            .reviewRating(itemReview != null ? itemReview.getRating() : null)
                            .build();
                })
                .toList() : List.of();

        return OrderResponseDTO.builder()
                .id(order.getId())
                .buyerId(order.getBuyer().getId())
                .buyerName(order.getBuyer().getFullName())
                .sellerId(order.getSeller() != null ? order.getSeller().getId() : null)
                .sellerName(order.getSeller() != null ? order.getSeller().getFullName() : null)
                .totalAmount(order.getTotalAmount())
                .commissionFee(order.getCommissionFee())
                .status(order.getStatus())
                .paymentMethod(payment != null ? payment.getGateway() : "PAYOS")
                .paymentStatus(payment != null ? payment.getStatus() : "PENDING")
                .orderCode(payment != null ? payment.getTransactionId() : null)
                .checkoutOrderId(order.getCheckoutOrder() != null ? order.getCheckoutOrder().getId() : null)
                .shippingInfo(shippingDTO)
                .items(itemDTOs)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getMyOrders(UUID buyerId) {
        List<Order> orders = orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
        if (orders.isEmpty()) return List.of();

        List<UUID> orderIds = orders.stream().map(Order::getId).toList();
        List<ShippingInfo> shippingInfos = shippingInfoRepository.findByIdIn(orderIds);
        Map<UUID, ShippingInfo> shippingMap = shippingInfos.stream()
                .collect(Collectors.toMap(ShippingInfo::getId, s -> s));

        List<OrderItem> orderItems = orderItemRepository.findByOrderIn(orders);
        Map<UUID, List<OrderItem>> itemsMap = orderItems.stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        return orders.stream()
                .map(o -> mapOrderToDTO(o, shippingMap.get(o.getId()), itemsMap.getOrDefault(o.getId(), List.of())))
                .toList();
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
        List<Order> orders = orderRepository.findAll();
        if (orders.isEmpty()) return List.of();

        List<UUID> orderIds = orders.stream().map(Order::getId).toList();
        List<ShippingInfo> shippingInfos = shippingInfoRepository.findByIdIn(orderIds);
        Map<UUID, ShippingInfo> shippingMap = shippingInfos.stream()
                .collect(Collectors.toMap(ShippingInfo::getId, s -> s));

        List<OrderItem> orderItems = orderItemRepository.findByOrderIn(orders);
        Map<UUID, List<OrderItem>> itemsMap = orderItems.stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        return orders.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(o -> mapOrderToDTO(o, shippingMap.get(o.getId()), itemsMap.getOrDefault(o.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public void updateOrderStatus(UUID id, String nextStatus, User currentUser) {
        Order order = orderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        String currentStatus = order.getStatus();
        Payment payment = paymentRepository.findByOrderId(id).orElse(null);
        if (payment == null && order.getCheckoutOrder() != null) {
            payment = paymentRepository.findByCheckoutOrderIdAndScope(order.getCheckoutOrder().getId(), "MARKETPLACE")
                    .or(() -> paymentRepository.findByCheckoutOrderIdAndScope(order.getCheckoutOrder().getId(), "ALL"))
                    .orElse(null);
        }

        if ("CANCELLED".equals(nextStatus)) {
            if (currentUser.getRole() != UserRole.ADMIN && !order.getBuyer().getId().equals(currentUser.getId())) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Bạn không có quyền hủy đơn hàng này");
            }
            if (!"PENDING".equals(currentStatus)) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chỉ được hủy đơn khi đơn chưa thanh toán hoặc chưa sản xuất");
            }
            if (payment != null && "SUCCESS".equals(payment.getStatus())) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn hàng đã thanh toán thành công, vui lòng liên hệ Admin để được hỗ trợ đối soát");
            }
            if (payment != null && "PAYOS".equals(payment.getGateway()) && payment.getTransactionId() != null) {
                payment.setStatus("CANCELLED");
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
            }

            if (order.getCheckoutOrder() != null) {
                // Đơn thuộc CheckoutOrder: giải phóng RESERVED, và nếu là COD đã COMMITTED thì khôi phục Product.stock (R2-07)
                List<InventoryReservation> resList = inventoryReservationRepository.findByCheckoutOrderId(order.getCheckoutOrder().getId());
                for (var res : resList) {
                    if ("RESERVED".equals(res.getStatus())) {
                        res.setStatus("CANCELLED");
                        res.setUpdatedAt(Instant.now());
                        inventoryReservationRepository.save(res);
                    } else if ("COMMITTED".equals(res.getStatus())) {
                        Product p = productRepository.findLockedById(res.getProduct().getId()).orElse(null);
                        if (p != null) {
                            p.setStock(p.getStock() + res.getQuantity());
                            productRepository.save(p);
                        }
                        res.setStatus("CANCELLED");
                        res.setUpdatedAt(Instant.now());
                        inventoryReservationRepository.save(res);
                    }
                }
            } else {
                // Đơn mua trực tiếp (standalone): Đã trừ kho lúc tạo đơn, mới khôi phục tồn kho vào Product.stock (R07)
                List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
                Map<UUID, Integer> restoreMap = new HashMap<>();
                for (OrderItem oi : orderItems) {
                    restoreMap.merge(oi.getProduct().getId(), oi.getQuantity(), Integer::sum);
                }
                List<UUID> sortedPids = new ArrayList<>(restoreMap.keySet());
                sortedPids.sort(UUID::compareTo);
                for (UUID pid : sortedPids) {
                    Product p = productRepository.findLockedById(pid).orElse(null);
                    if (p != null) {
                        p.setStock(p.getStock() + restoreMap.get(pid));
                        productRepository.save(p);
                    }
                }
            }
        } else {
            if (currentUser.getRole() != UserRole.ADMIN) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Chỉ quản trị viên mới có thể chuyển tiến trình đơn");
            }

            // Production guard: Online order must be PAID before production; COD can enter preparation (R08)
            boolean isCod = (payment != null && "COD".equalsIgnoreCase(payment.getGateway()))
                    || (order.getCheckoutOrder() != null && "COD".equalsIgnoreCase(order.getCheckoutOrder().getPaymentMethod()));

            if (("PREPARING".equals(nextStatus) || "PRINTING".equals(nextStatus)) && "PENDING".equals(currentStatus)) {
                if (!isCod) {
                    throw new ApiException(CommonErrorCode.INVALID_INPUT,
                            "Đơn thanh toán trực tuyến phải hoàn tất thanh toán (PAID) trước khi đưa vào sản xuất");
                }
            }

            boolean isValidTransition = switch (currentStatus) {
                case "PAID", "PENDING" -> "PREPARING".equals(nextStatus) || "PRINTING".equals(nextStatus);
                case "PREPARING" -> "PRINTING".equals(nextStatus) || "SHIPPING".equals(nextStatus);
                case "PRINTING" -> "SHIPPING".equals(nextStatus);
                case "SHIPPING" -> "COMPLETED".equals(nextStatus);
                default -> false;
            };

            if (!isValidTransition) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chuyển trạng thái đơn không hợp lệ từ " + currentStatus + " sang " + nextStatus);
            }
            // COD thanh toán được xác nhận riêng bởi Admin qua confirmCodPayment có audit (không tự động coi DELIVERED là đã thu)
        }

        order.setStatus(nextStatus);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        if (order.getCheckoutOrder() != null) {
            syncParentCheckoutOrder(order.getCheckoutOrder().getId());
        }

        if ("COMPLETED".equals(nextStatus)) {
            completeRewards(id);
        }

        notificationService.sendNotification(order.getBuyer(),
                "Cập nhật đơn hàng",
                "Đơn hàng " + id + " đã chuyển sang trạng thái: " + nextStatus,
                "ORDER",
                "/orders");
    }

    private void syncParentCheckoutOrder(UUID checkoutOrderId) {
        if (checkoutOrderId == null || checkoutOrderRepository == null) return;
        checkoutOrderRepository.findById(checkoutOrderId).ifPresent(parent -> {
            List<Order> childOrders = orderRepository.findByCheckoutOrderId(checkoutOrderId);
            List<CustomOrder> childCustoms = (customOrderRepository != null)
                    ? customOrderRepository.findByCheckoutOrderId(checkoutOrderId) : Collections.emptyList();

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

    @Override
    @Transactional
    public RewardCompletionResponseDTO confirmCodPayment(UUID orderId, User adminUser) {
        if (adminUser.getRole() != UserRole.ADMIN) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Chỉ quản trị viên mới có quyền xác nhận thu tiền COD");
        }
        Order order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        if ("CANCELLED".equals(order.getStatus())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Không thể thu tiền COD cho đơn đã hủy");
        }

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .or(() -> order.getCheckoutOrder() != null ? paymentRepository.findByCheckoutOrderIdAndScope(order.getCheckoutOrder().getId(), "MARKETPLACE") : Optional.empty())
                .orElseThrow(() -> new ApiException(CommonErrorCode.INVALID_INPUT, "Không tìm thấy thông tin thanh toán của đơn hàng"));

        if (!"COD".equalsIgnoreCase(payment.getGateway())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn hàng này không sử dụng hình thức COD");
        }
        if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            log.info("Đơn hàng {} đã được xác nhận thu tiền COD trước đó.", orderId);
        } else {
            payment.setStatus("SUCCESS");
            payment.setPaidAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            log.info("Admin {} đã xác nhận thu tiền COD thành công cho đơn hàng {}", adminUser.getUsername(), orderId);

            if (!"COMPLETED".equals(order.getStatus()) && !"DELIVERED".equals(order.getStatus())) {
                order.setStatus("PAID");
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);
            }
            if (order.getCheckoutOrder() != null && checkoutOrderRepository != null) {
                CheckoutOrder co = order.getCheckoutOrder();
                co.setMarketplaceStatus("PAID");
                co.setUpdatedAt(Instant.now());
                checkoutOrderRepository.save(co);
            }
        }

        if ("COMPLETED".equals(order.getStatus())) {
            return completeRewards(orderId);
        }
        return RewardCompletionResponseDTO.builder()
                .orderId(order.getId())
                .customerId(order.getBuyer().getId())
                .customerName(order.getBuyer().getFullName())
                .rewardPointsEarned(0)
                .totalRewardPoints(order.getBuyer().getRewardPoints() != null ? order.getBuyer().getRewardPoints() : 0)
                .alreadyProcessed(false)
                .build();
    }
}
