package com.fpt.printhub_3d.dto.address;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Thông tin địa chỉ giao nhận hàng")
public record AddressResponseDTO(
        Long id,
        String recipientName,
        String phone,
        String addressLine,
        String province,
        boolean isDefault
) {}
