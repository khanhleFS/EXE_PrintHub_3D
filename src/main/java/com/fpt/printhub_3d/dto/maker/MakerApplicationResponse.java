package com.fpt.printhub_3d.dto.maker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MakerApplicationResponse {
    private UUID applicationId;
    private String extractedName;
    private String extractedIdNumber;
    private String status;
}
