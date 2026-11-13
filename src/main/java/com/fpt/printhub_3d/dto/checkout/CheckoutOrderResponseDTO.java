package com.fpt.printhub_3d.dto.checkout;

import com.fpt.printhub_3d.dto.custom_prints.CustomOrderDetailResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import com.fpt.printhub_3d.dto.order.BlockedActionDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin chi tiết Đơn thanh toán cha")
public record CheckoutOrderResponseDTO(
        UUID id,
        UUID userId,
        String buyerName,
        String recipientName,
        String recipientPhone,
        String orderType,
        String shippingAddress,
        String shippingMode,
        String status,
        BigDecimal itemsSubtotal,
        BigDecimal totalAmount,
        BigDecimal shippingFee,
        String paymentMethod,
        String paymentStatus,
        String cancelReason,
        Long summaryVersion,
        Integer itemCount,
        Instant createdAt,
        Instant updatedAt,
        List<CustomOrderResponseDTO> customOrders,
        List<CustomOrderDetailResponseDTO> customOrderDetails,
        List<OrderResponseDTO> marketplaceOrders,
        List<String> allowedActions,
        List<BlockedActionDTO> blockedActions,
        boolean allCustomQuoted,
        boolean readyForConfirm,
        String paymentLinkUrl,
        BigDecimal discountAmount,
        BigDecimal shippingDiscountAmount,
        UUID appliedSubscriptionId,
        String appliedSubscriptionName,
        Boolean priorityProduction,
        String cancelledByRole,
        Instant cancelledAt
) {}
