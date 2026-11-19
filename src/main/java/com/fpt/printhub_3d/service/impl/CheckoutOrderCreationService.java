package com.fpt.printhub_3d.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CheckoutOrderErrorCode;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.CloudinaryService;
import com.fpt.printhub_3d.service.InventoryService;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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
@Component
@RequiredArgsConstructor
public class CheckoutOrderCreationService {

    private static final ObjectMapper FALLBACK_OBJECT_MAPPER = new ObjectMapper();

    private final CheckoutOrderRepository checkoutOrderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomOrderRepository customOrderRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final CheckoutOrderMapper checkoutOrderMapper;
    private final CheckoutOrderQuotationService checkoutOrderQuotationService;

    @Nullable
    private final NotificationService notificationService;
    @Nullable
    private final ObjectMapper objectMapper;
    @Nullable
    private final CloudinaryService cloudinaryService;
    @Nullable
    private final SubscriptionBenefitService subscriptionBenefitService;

    public CheckoutOrder createCheckoutOrderEntity(User user, CheckoutOrderCreateRequestDTO request) {
        log.info("Tạo CheckoutOrder cho user: {}", user.getId());

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<CheckoutOrder> existing = checkoutOrderRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                log.info("Request idempotent: Đơn hàng với idempotencyKey [{}] đã tồn tại, trả lại đơn cũ", request.idempotencyKey());
                return existing.get();
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
        BigDecimal baseShipFee = customLines.isEmpty() ? checkoutOrderMapper.resolveDefaultShippingFee() : BigDecimal.ZERO;
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
                checkoutOrderQuotationService.settleZeroAmountCheckout(checkoutOrder, checkoutOrder.getUpdatedAt());
            }
            checkoutOrder = checkoutOrderRepository.save(checkoutOrder);
        }

        for (CartItem cItem : customLines) {
            CustomOrder customOrder = createChildCustomOrder(user, checkoutOrder, request, cItem);
            customOrderRepository.save(customOrder);
        }

        if (!customLines.isEmpty() && notificationService != null) {
            notifyAdminsNewCustomCheckout(checkoutOrder, user, customLines.size());
        }

        boolean zeroAmountSettlement = checkoutOrder.getZeroAmountSettledAt() != null;
        if (!marketplaceLines.isEmpty()) {
            User fallbackSeller = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN)
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

        cartItemRepository.deleteAll(selectedItems);
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        if ("CONFIRMED".equals(checkoutOrder.getStatus()) && "PAYOS".equalsIgnoreCase(checkoutOrder.getPaymentMethod())) {
            checkoutOrderQuotationService.prepareCheckoutPayment(checkoutOrder);
        }

        return checkoutOrder;
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

    private JsonNode parseJsonSafely(String raw) {
        if (raw == null || !raw.trim().startsWith("{")) return null;
        try {
            return (objectMapper != null ? objectMapper : FALLBACK_OBJECT_MAPPER).readTree(raw);
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
