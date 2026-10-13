package com.fpt.printhub_3d.dto.checkout;

import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
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
        String shippingAddress,
        String shippingMode,
        String status,
        BigDecimal totalAmount,
        BigDecimal shippingFee,
        String paymentMethod,
        Long summaryVersion,
        Instant createdAt,
        Instant updatedAt,
        List<CustomOrderResponseDTO> customOrders,
        List<OrderResponseDTO> marketplaceOrders,
        boolean allCustomQuoted,
        boolean readyForConfirm,
        String paymentLinkUrl
) {}
