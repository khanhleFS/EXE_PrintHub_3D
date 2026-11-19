package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.dto.order.*;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.InventoryService;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreationService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");
    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("30000");
    private static final Set<String> VALID_COLORS = Set.of("GREEN", "BLUE", "PINK", "WHITE", "BLACK");

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShippingInfoRepository shippingInfoRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;

    @Nullable
    private final SubscriptionBenefitService subscriptionBenefitService;
    @Nullable
    private final OrderRewardService orderRewardService;

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
}
