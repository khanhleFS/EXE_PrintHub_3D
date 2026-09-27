package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Chi tiết đầy đủ đơn in custom kèm thông số cá nhân hóa và thanh toán")
public record CustomOrderDetailResponseDTO(
        UUID id,
        UUID buyerId,
        String buyerName,
        UUID makerId,
        String makerName,
        String requirements,
        int quantity,
        String shippingAddress,
        String attachmentUrl,
        BigDecimal quotedPrice,
        String status,
        String rulerModel,
        String customName,
        String customStudentId,
        String color,
        String fontStyle,
        String paymentMethod,
        String paymentStatus,
        Instant createdAt,
        Instant updatedAt
) {}
