package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin đánh giá dịch vụ in 3D của một dòng CustomOrder")
public record CustomOrderReviewResponseDTO(
        Long id,
        UUID customOrderId,
        UUID checkoutOrderId,
        String reviewTargetType,
        UUID reviewerId,
        String reviewerName,
        Short rating,
        String comment,
        List<String> imageUrls,
        boolean canEdit,
        Instant editDeadlineAt,
        Instant createdAt,
        Instant updatedAt
) {
}
