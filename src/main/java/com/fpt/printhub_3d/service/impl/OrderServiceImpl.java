package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.dto.order.OrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.order.OrderItemRequestDTO;
import com.fpt.printhub_3d.dto.order.OrderItemResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.dto.order.ShippingInfoResponseDTO;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.OrderItem;
import com.fpt.printhub_3d.entity.Product;
import com.fpt.printhub_3d.entity.ShippingInfo;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.OrderItemRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.ProductRepository;
import com.fpt.printhub_3d.repository.ShippingInfoRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.service.OrderService;
import com.fpt.printhub_3d.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05"); // 5% commission fee
    private static final BigDecimal REWARD_POINT_UNIT = new BigDecimal("10000");

    private static final Set<String> VALID_COLORS = Set.of("GREEN", "BLUE", "PINK", "WHITE", "BLACK");

    @Override
    @Transactional
    public List<OrderResponseDTO> createOrders(OrderCreateRequestDTO request, User buyer) {
        log.info("Khởi tạo đơn hàng từ buyer [{} - {}]", buyer.getId(), buyer.getFullName());

        if (request.items() == null || request.items().isEmpty()) {
            throw new ApiException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }

        // Tải toàn bộ sản phẩm cần mua và xác thực
        List<UUID> productIds = request.items().stream()
                .map(OrderItemRequestDTO::productId)
                .distinct()
                .toList();

        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.size()) {
            throw new ApiException(OrderErrorCode.PRODUCT_NOT_FOUND);
        }

        Map<UUID, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Phân nhóm sản phẩm theo người bán (Seller/Maker)
        Map<User, List<OrderItemBuild>> itemsBySeller = new HashMap<>();

        for (OrderItemRequestDTO itemReq : request.items()) {
            Product product = productMap.get(itemReq.productId());

            // Kiểm tra trạng thái sản phẩm
            if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
                throw new ApiException(
                        OrderErrorCode.PRODUCT_NOT_FOUND,
                        "Sản phẩm '" + product.getTitle() + "' không còn hoạt động hoặc đã ngừng bán."
                );
            }

            // Kiểm tra người bán hoạt động hay không
            if (product.getSeller() == null || !Boolean.TRUE.equals(product.getSeller().getIsActive())) {
                throw new ApiException(
                        OrderErrorCode.SELLER_INACTIVE,
                        "Người bán của sản phẩm '" + product.getTitle() + "' không hoạt động."
                );
            }

            // Kiểm tra tồn kho
            if (product.getStock() < itemReq.quantity()) {
                throw new ApiException(
                        OrderErrorCode.OUT_OF_STOCK,
                        "Sản phẩm '" + product.getTitle() + "' không đủ số lượng tồn kho. Yêu cầu: " + itemReq.quantity() + ", Còn lại: " + product.getStock()
                );
            }

            // Kiểm tra màu sắc cá nhân hóa hợp lệ
            if (itemReq.color() != null && !itemReq.color().isBlank()) {
                String colorUpper = itemReq.color().trim().toUpperCase();
                if (!VALID_COLORS.contains(colorUpper)) {
                    throw new ApiException(com.fpt.printhub_3d.common.exception.CommonErrorCode.INVALID_INPUT,
                            "Màu sắc '" + itemReq.color() + "' không hợp lệ. Các màu hợp lệ: GREEN, BLUE, PINK, WHITE, BLACK");
                }
            }

            // Đưa vào nhóm của Seller tương ứng
            User seller = product.getSeller();
            itemsBySeller.computeIfAbsent(seller, k -> new ArrayList<>())
                    .add(new OrderItemBuild(product, itemReq.quantity(), itemReq.color(), itemReq.engravingText()));
        }

        List<OrderResponseDTO> createdOrders = new ArrayList<>();

        // Tạo từng đơn hàng cho từng nhóm Seller
        for (Map.Entry<User, List<OrderItemBuild>> entry : itemsBySeller.entrySet()) {
            User seller = entry.getKey();
            List<OrderItemBuild> orderItemsBuild = entry.getValue();

            // Tính tổng tiền & phí hoa hồng của đơn hàng này
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (OrderItemBuild item : orderItemsBuild) {
                BigDecimal itemCost = item.product.getPrice().multiply(BigDecimal.valueOf(item.quantity));
                totalAmount = totalAmount.add(itemCost);
            }
            BigDecimal commissionFee = totalAmount.multiply(COMMISSION_RATE);

            // Khởi tạo đơn hàng (Order)
            Order order = new Order();
            order.setBuyer(buyer);
            order.setSeller(seller);
            order.setTotalAmount(totalAmount);
            order.setCommissionFee(commissionFee);
            order.setStatus("PENDING");
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());

            Order savedOrder = orderRepository.save(order);

            // Khởi tạo & Lưu thông tin giao hàng (ShippingInfo)
            ShippingInfo shippingInfo = new ShippingInfo();
            shippingInfo.setOrders(savedOrder);
            shippingInfo.setRecipientName(request.recipientName());
            shippingInfo.setPhone(request.phone());
            shippingInfo.setAddress(request.address());
            shippingInfo.setProvince(request.province());

            shippingInfoRepository.save(shippingInfo);

            List<OrderItemResponseDTO> itemResponses = new ArrayList<>();

            // Trừ tồn kho sản phẩm & Lưu chi tiết đơn hàng (OrderItem)
            for (OrderItemBuild itemBuild : orderItemsBuild) {
                Product product = itemBuild.product;
                int originalStock = product.getStock();
                int newStock = originalStock - itemBuild.quantity;

                product.setStock(newStock);
                product.setUpdatedAt(Instant.now());
                productRepository.save(product);

                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(savedOrder);
                orderItem.setProduct(product);
                orderItem.setQuantity(itemBuild.quantity);
                orderItem.setUnitPrice(product.getPrice());
                orderItem.setColor(itemBuild.color);
                orderItem.setEngravingText(itemBuild.engravingText);

                OrderItem savedItem = orderItemRepository.save(orderItem);

                itemResponses.add(OrderItemResponseDTO.builder()
                        .id(savedItem.getId())
                        .productId(product.getId())
                        .productTitle(product.getTitle())
                        .quantity(savedItem.getQuantity())
                        .unitPrice(savedItem.getUnitPrice())
                        .subTotal(savedItem.getUnitPrice().multiply(BigDecimal.valueOf(savedItem.getQuantity())))
                        .color(savedItem.getColor())
                        .engravingText(savedItem.getEngravingText())
                        .build());
            }

            // Xử lý thanh toán trực tiếp cho đơn hàng này
            String paymentMethod = request.paymentMethod().trim().toUpperCase();

            if ("PAYOS".equals(paymentMethod)) {
                // Không tạo bản ghi Payment ở đây, sẽ được tạo khi gọi API /api/payments/create-link
            } else if ("COD".equals(paymentMethod)) {
                Payment payment = new Payment();
                payment.setOrder(savedOrder);
                payment.setAmount(totalAmount);
                payment.setGateway("COD");
                payment.setStatus("PENDING");
                payment.setTransactionId(null);
                payment.setCreatedAt(Instant.now());
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
            } else {
                throw new ApiException(com.fpt.printhub_3d.common.exception.CommonErrorCode.INVALID_INPUT,
                        "Phương thức thanh toán '" + request.paymentMethod() + "' không hợp lệ. Chỉ chấp nhận COD hoặc PAYOS");
            }

            // Tạo response DTO cho đơn hàng này
            createdOrders.add(OrderResponseDTO.builder()
                    .id(savedOrder.getId())
                    .buyerId(buyer.getId())
                    .buyerName(buyer.getFullName())
                    .sellerId(seller.getId())
                    .sellerName(seller.getFullName())
                    .totalAmount(savedOrder.getTotalAmount())
                    .commissionFee(savedOrder.getCommissionFee())
                    .status(savedOrder.getStatus())
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

            log.info("Tạo đơn hàng thành công ID: [{}], Tổng tiền: {} VND cho seller [{}]",
                    savedOrder.getId(), savedOrder.getTotalAmount(), seller.getFullName());
        }

        return createdOrders;
    }

    @Override
    @Transactional
    public RewardCompletionResponseDTO completeRewards(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!"COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new ApiException(OrderErrorCode.ORDER_NOT_COMPLETED);
        }

        User buyer = order.getBuyer();

        if (Boolean.TRUE.equals(order.getRewardProcessed())) {
            return RewardCompletionResponseDTO.builder()
                    .orderId(order.getId())
                    .customerId(buyer.getId())
                    .customerName(buyer.getFullName())
                    .rewardPointsEarned(order.getRewardPointsEarned())
                    .totalRewardPoints(buyer.getRewardPoints())
                    .alreadyProcessed(true)
                    .build();
        }

        int earnedPoints = order.getTotalAmount()
                .divideToIntegralValue(REWARD_POINT_UNIT)
                .intValue();

        int currentPoints = buyer.getRewardPoints() == null ? 0 : buyer.getRewardPoints();
        buyer.setRewardPoints(currentPoints + earnedPoints);
        userRepository.save(buyer);

        order.setRewardProcessed(true);
        order.setRewardPointsEarned(earnedPoints);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        log.info("Cộng [{}] điểm thưởng cho customer [{}] từ order [{}]",
                earnedPoints, buyer.getId(), order.getId());

        return RewardCompletionResponseDTO.builder()
                .orderId(order.getId())
                .customerId(buyer.getId())
                .customerName(buyer.getFullName())
                .rewardPointsEarned(earnedPoints)
                .totalRewardPoints(buyer.getRewardPoints())
                .alreadyProcessed(false)
                .build();
    }

    @Override
    public List<OrderResponseDTO> getMyOrders(UUID buyerId) {
        log.info("Lấy lịch sử đơn hàng của buyer ID: {}", buyerId);

        List<Order> orders = orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
        if (orders.isEmpty()) {
            return List.of();
        }

        List<UUID> orderIds = orders.stream().map(Order::getId).toList();

        // Batch fetch ShippingInfo
        List<ShippingInfo> shippingInfos = shippingInfoRepository.findByIdIn(orderIds);
        Map<UUID, ShippingInfo> shippingMap = shippingInfos.stream()
                .collect(Collectors.toMap(ShippingInfo::getId, s -> s));

        // Batch fetch OrderItem
        List<OrderItem> orderItems = orderItemRepository.findByOrderIn(orders);
        Map<UUID, List<OrderItem>> itemsMap = orderItems.stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        List<OrderResponseDTO> responseList = new ArrayList<>();
        for (Order order : orders) {
            ShippingInfo shippingInfo = shippingMap.get(order.getId());
            List<OrderItem> items = itemsMap.getOrDefault(order.getId(), List.of());

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

            List<OrderItemResponseDTO> itemDTOs = items.stream()
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
                    .toList();

            responseList.add(OrderResponseDTO.builder()
                    .id(order.getId())
                    .buyerId(order.getBuyer().getId())
                    .buyerName(order.getBuyer().getFullName())
                    .sellerId(order.getSeller().getId())
                    .sellerName(order.getSeller().getFullName())
                    .totalAmount(order.getTotalAmount())
                    .commissionFee(order.getCommissionFee())
                    .status(order.getStatus())
                    .shippingInfo(shippingDTO)
                    .items(itemDTOs)
                    .createdAt(order.getCreatedAt())
                    .updatedAt(order.getUpdatedAt())
                    .build());
        }

        return responseList;
    }

    // Helper class để truyền dữ liệu nội bộ
    private static class OrderItemBuild {
        final Product product;
        final int quantity;
        final String color;
        final String engravingText;

        OrderItemBuild(Product product, int quantity, String color, String engravingText) {
            this.product = product;
            this.quantity = quantity;
            this.color = color != null ? color.trim().toUpperCase() : null;
            this.engravingText = engravingText;
        }
    }
}
