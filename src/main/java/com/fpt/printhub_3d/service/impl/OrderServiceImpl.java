package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.dto.order.*;
import com.fpt.printhub_3d.entity.*;
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

        for (Map.Entry<UUID, Integer> entry : totalRequestedStock.entrySet()) {
            Product p = productRepository.findById(entry.getKey()).orElseThrow();
            if (p.getStock() < entry.getValue()) {
                throw new ApiException(OrderErrorCode.OUT_OF_STOCK,
                        "Sản phẩm '" + p.getTitle() + "' không đủ số lượng tồn kho (còn: " + p.getStock() + ", yêu cầu: " + entry.getValue() + ")");
            }
        }

        for (Map.Entry<UUID, Integer> entry : totalRequestedStock.entrySet()) {
            Product p = productRepository.findById(entry.getKey()).orElseThrow();
            p.setStock(p.getStock() - entry.getValue());
            productRepository.save(p);
        }

        Map<UUID, List<ItemWithProduct>> itemsBySeller = validatedItems.stream()
                .collect(Collectors.groupingBy(iwp -> iwp.product().getSeller().getId()));

        List<OrderResponseDTO> createdOrders = new ArrayList<>();

        for (Map.Entry<UUID, List<ItemWithProduct>> sellerEntry : itemsBySeller.entrySet()) {
            List<ItemWithProduct> sellerItems = sellerEntry.getValue();
            User seller = sellerItems.getFirst().product().getSeller();

            BigDecimal totalAmount = BigDecimal.ZERO;
            for (ItemWithProduct iwp : sellerItems) {
                BigDecimal itemTotal = iwp.product().getPrice().multiply(BigDecimal.valueOf(iwp.item().quantity()));
                totalAmount = totalAmount.add(itemTotal);
            }

            BigDecimal commissionFee = totalAmount.multiply(COMMISSION_RATE).setScale(0, RoundingMode.HALF_UP);

            Order order = new Order();
            order.setBuyer(buyer);
            order.setSeller(seller);
            order.setTotalAmount(totalAmount);
            order.setCommissionFee(commissionFee);
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
            for (ItemWithProduct iwp : sellerItems) {
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

        int earnedPoints = order.getTotalAmount()
                .divideToIntegralValue(REWARD_POINT_UNIT)
                .intValue();

        int currentPoints = buyer.getRewardPoints() == null ? 0 : buyer.getRewardPoints();
        buyer.setRewardPoints(currentPoints + earnedPoints);
        userRepository.save(buyer);

        var wallet = pointWalletRepository.findById(buyer.getId()).orElseGet(() ->
                PointWallet.builder().userId(buyer.getId()).user(buyer).balance(currentPoints).build());
        wallet.setBalance(wallet.getBalance() + earnedPoints);
        wallet.setUpdatedAt(java.time.LocalDateTime.now());
        pointWalletRepository.save(wallet);

        pointTransactionRepository.save(PointTransaction.builder()
                .pointWallet(wallet).amount(earnedPoints)
                .type(com.fpt.printhub_3d.entity.Enumeration.PointTransactionType.EARN)
                .description("Hoàn thành đơn " + order.getId()).createdAt(java.time.LocalDateTime.now()).build());

        order.setRewardProcessed(true);
        order.setRewardPointsEarned(earnedPoints);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        return RewardCompletionResponseDTO.builder()
                .orderId(order.getId())
                .customerId(buyer.getId())
                .customerName(buyer.getFullName())
                .rewardPointsEarned(earnedPoints)
                .totalRewardPoints(buyer.getRewardPoints())
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

        List<OrderItemResponseDTO> itemDTOs = items != null ? items.stream()
                .map(item -> OrderItemResponseDTO.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productTitle(item.getProduct().getTitle())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .color(item.getColor())
                        .engravingText(item.getEngravingText())
                        .build())
                .toList() : List.of();

        return OrderResponseDTO.builder()
                .id(order.getId())
                .buyerId(order.getBuyer().getId())
                .buyerName(order.getBuyer().getFullName())
                .sellerId(order.getSeller().getId())
                .sellerName(order.getSeller().getFullName())
                .totalAmount(order.getTotalAmount())
                .commissionFee(order.getCommissionFee())
                .status(order.getStatus())
                .paymentMethod(payment != null ? payment.getGateway() : "PAYOS")
                .paymentStatus(payment != null ? payment.getStatus() : "PENDING")
                .orderCode(payment != null ? payment.getTransactionId() : null)
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
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        String currentStatus = order.getStatus();
        Payment payment = paymentRepository.findByOrderId(id).orElse(null);

        if ("CANCELLED".equals(nextStatus)) {
            if (currentUser.getRole() != UserRole.ADMIN && !order.getBuyer().getId().equals(currentUser.getId())) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Bạn không có quyền hủy đơn hàng này");
            }
            if (!"PENDING".equals(currentStatus)) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chỉ được hủy đơn khi đơn chưa thanh toán hoặc chưa sản xuất");
            }
            if (payment != null && "PAYOS".equals(payment.getGateway()) && payment.getTransactionId() != null) {
                payment.setStatus("CANCELLED");
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
            }
        } else {
            if (currentUser.getRole() != UserRole.ADMIN) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Chỉ quản trị viên mới có thể chuyển tiến trình đơn");
            }
            String expected = switch (currentStatus) {
                case "PAID" -> "PRINTING";
                case "PENDING" -> "PRINTING";
                case "PRINTING" -> "SHIPPING";
                case "SHIPPING" -> "COMPLETED";
                default -> "";
            };

            if (!expected.equals(nextStatus)) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chuyển trạng thái đơn không hợp lệ từ " + currentStatus + " sang " + nextStatus);
            }
            if ("COMPLETED".equals(nextStatus) && payment != null && "COD".equals(payment.getGateway())) {
                payment.setStatus("SUCCESS");
                payment.setPaidAt(Instant.now());
                paymentRepository.save(payment);
            }
        }

        order.setStatus(nextStatus);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        if ("COMPLETED".equals(nextStatus)) {
            completeRewards(id);
        }

        notificationService.sendNotification(order.getBuyer(),
                "Cập nhật đơn hàng",
                "Đơn hàng " + id + " đã chuyển sang trạng thái: " + nextStatus,
                "ORDER",
                "/orders");
    }
}
