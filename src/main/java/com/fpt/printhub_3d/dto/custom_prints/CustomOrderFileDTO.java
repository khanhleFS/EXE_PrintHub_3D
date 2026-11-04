package com.fpt.printhub_3d.dto.custom_prints;

import lombok.Builder;

@Builder
public record CustomOrderFileDTO(
        byte[] data,
        String filename,
        String contentType
) {}

