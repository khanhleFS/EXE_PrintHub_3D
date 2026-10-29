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

    @Override
    public CheckoutOrderResponseDTO createCheckoutOrder(User user, CheckoutOrderCreateRequestDTO request) {
        log.info("Tạo CheckoutOrder cho user: {}", user.getId());

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

        CheckoutOrder checkoutOrder = new CheckoutOrder();
        checkoutOrder.setUser(user);
        checkoutOrder.setShippingAddress(request.shippingAddress());
        checkoutOrder.setShippingMode(shippingMode);
        checkoutOrder.setStatus(customLines.isEmpty() ? "CONFIRMED" : "PENDING_QUOTE");
        checkoutOrder.setShippingFee(BigDecimal.valueOf(30000));
        checkoutOrder.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod().toUpperCase() : "PAYOS");
        checkoutOrder.setSummaryVersion(1L);
        checkoutOrder.setCreatedAt(Instant.now());
        checkoutOrder.setUpdatedAt(Instant.now());

        BigDecimal marketSubtotal = marketplaceLines.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal customSubtotal = customLines.stream()
                .map(i -> (i.getCustomPrice() != null ? i.getCustomPrice() : BigDecimal.ZERO).multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalTotal = marketSubtotal.add(checkoutOrder.getShippingFee());
        checkoutOrder.setTotalAmount(finalTotal);

        if (!customLines.isEmpty()) {
            checkoutOrder.setStatus("PENDING_QUOTE");
        } else {
            checkoutOrder.setStatus("CONFIRMED");
        }

        checkoutOrder = checkoutOrderRepository.save(checkoutOrder);

        // Tạo CustomOrder cho các dòng thiết kế custom
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
            customOrder.setPaymentMethod(request.paymentMethod());
            // An toàn: Không chấp nhận giá do client tự gửi làm báo giá chính thức
            customOrder.setQuotedPrice(null);
            customOrder.setStatus("REQUESTED");
            customOrder.setCreatedAt(Instant.now());
            customOrder.setUpdatedAt(Instant.now());
            customOrderRepository.save(customOrder);
        }

        // Tạo Order cho các dòng sản phẩm Marketplace
        if (!marketplaceLines.isEmpty()) {
            Map<UUID, List<CartItem>> itemsBySeller = marketplaceLines.stream()
                    .collect(Collectors.groupingBy(i -> i.getProduct().getSeller().getId()));

            for (Map.Entry<UUID, List<CartItem>> entry : itemsBySeller.entrySet()) {
                User seller = entry.getValue().getFirst().getProduct().getSeller();
                BigDecimal sellerTotal = entry.getValue().stream()
                        .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                Order order = new Order();
                order.setBuyer(user);
                order.setSeller(seller);
                order.setCheckoutOrder(checkoutOrder);
                order.setStatus("PENDING");
                order.setTotalAmount(sellerTotal);
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
            }
        }

        // Xóa các dòng đã chọn khỏi giỏ hàng
        cartItemRepository.deleteAll(selectedItems);
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        String paymentLinkUrl = null;
        if ("CONFIRMED".equals(checkoutOrder.getStatus()) && "PAYOS".equalsIgnoreCase(checkoutOrder.getPaymentMethod())) {
            try {
                CreatePaymentLinkRequestDTO payRequest = CreatePaymentLinkRequestDTO.builder()
                        .orderId(checkoutOrder.getId())
                        .orderType("CHECKOUT_ORDER")
                        .paymentOption("FULL")
                        .customAmount(finalTotal)
                        .description("Don PrintHub " + checkoutOrder.getId().toString().substring(0, 8))
                        .build();
                CreatePaymentLinkResponseDTO payResp = paymentService.createPaymentLink(user.getId(), payRequest);
                paymentLinkUrl = payResp.paymentLinkUrl();
            } catch (Exception e) {
                log.error("Lỗi tạo link PayOS khi tạo checkout order {}: {}", checkoutOrder.getId(), e.getMessage());
            }
        }

        return toDTO(checkoutOrder, paymentLinkUrl);
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

        if (request.summaryVersion() != null && !checkoutOrder.getSummaryVersion().equals(request.summaryVersion())) {
            throw new ApiException(CheckoutOrderErrorCode.CONFIRMATION_VERSION_CONFLICT);
        }

        List<CustomOrder> customList = customOrderRepository.findByCheckoutOrderId(checkoutOrder.getId());
        for (CustomOrder c : customList) {
            if (c.getQuotedPrice() == null) {
                throw new ApiException(CheckoutOrderErrorCode.CUSTOM_NOT_ALL_QUOTED);
            }
        }

        List<Order> orderList = orderRepository.findByCheckoutOrderId(checkoutOrder.getId());
        for (Order o : orderList) {
            List<OrderItem> items = orderItemRepository.findByOrderId(o.getId());
            for (OrderItem oi : items) {
                Product p = productRepository.findById(oi.getProduct().getId())
                        .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK));
                if (p.getPrice().compareTo(oi.getUnitPrice()) != 0) {
                    throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_PRICE_CHANGED);
                }
                if (p.getStock() < oi.getQuantity()) {
                    throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK);
                }
            }
        }

        // Khấu trừ tồn kho marketplace trong transaction
        for (Order o : orderList) {
            List<OrderItem> items = orderItemRepository.findByOrderId(o.getId());
            for (OrderItem oi : items) {
                Product p = productRepository.findById(oi.getProduct().getId()).orElseThrow();
                p.setStock(p.getStock() - oi.getQuantity());
                productRepository.save(p);
            }
        }

        // Tính tổng tiền sau báo giá
        BigDecimal customTotal = customList.stream()
                .map(c -> c.getQuotedPrice().multiply(BigDecimal.valueOf(c.getQuantity() != null ? c.getQuantity() : 1)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal marketplaceTotal = orderList.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingFee = checkoutOrder.getShippingFee() != null ? checkoutOrder.getShippingFee() : BigDecimal.valueOf(30000);
        BigDecimal finalTotal = customTotal.add(marketplaceTotal).add(shippingFee);

        checkoutOrder.setTotalAmount(finalTotal);
        checkoutOrder.setStatus("CONFIRMED");
        if (request.paymentMethod() != null) {
            checkoutOrder.setPaymentMethod(request.paymentMethod().toUpperCase());
        }
        checkoutOrder.setSummaryVersion(checkoutOrder.getSummaryVersion() + 1);
        checkoutOrder.setUpdatedAt(Instant.now());
        checkoutOrderRepository.save(checkoutOrder);

        for (CustomOrder c : customList) {
            if (!"PAID".equals(c.getStatus())) {
                c.setStatus("ACCEPTED");
                c.setUpdatedAt(Instant.now());
                customOrderRepository.save(c);
            }
        }

        String paymentLinkUrl = null;
        if ("PAYOS".equalsIgnoreCase(checkoutOrder.getPaymentMethod())) {
            try {
                CreatePaymentLinkRequestDTO payRequest = CreatePaymentLinkRequestDTO.builder()
                        .orderId(checkoutOrder.getId())
                        .orderType("CHECKOUT_ORDER")
                        .paymentOption("FULL")
                        .customAmount(finalTotal)
                        .description("Don PrintHub " + checkoutOrder.getId().toString().substring(0, 8))
                        .build();
                CreatePaymentLinkResponseDTO payResp = paymentService.createPaymentLink(user.getId(), payRequest);
                paymentLinkUrl = payResp.paymentLinkUrl();
            } catch (Exception e) {
                log.error("Lỗi tạo link PayOS cho checkout order {}: {}", checkoutOrder.getId(), e.getMessage());
            }
        } else if ("COD".equalsIgnoreCase(checkoutOrder.getPaymentMethod())) {
            checkoutOrder.setStatus("PROCESSING");
            checkoutOrderRepository.save(checkoutOrder);
        }

        return toDTO(checkoutOrder, paymentLinkUrl);
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
                    .sellerId(o.getSeller().getId())
                    .sellerName(o.getSeller().getFullName())
                    .totalAmount(o.getTotalAmount())
                    .commissionFee(o.getCommissionFee())
                    .status(o.getStatus())
                    .items(itemDTOs)
                    .createdAt(o.getCreatedAt())
                    .updatedAt(o.getUpdatedAt())
                    .build();
        }).toList();

        boolean allCustomQuoted = customs.isEmpty() || customs.stream().allMatch(c -> c.getQuotedPrice() != null);
        boolean readyForConfirm = "PENDING_QUOTE".equals(order.getStatus()) && allCustomQuoted;

        return CheckoutOrderResponseDTO.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .shippingAddress(order.getShippingAddress())
                .shippingMode(order.getShippingMode())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
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
}
