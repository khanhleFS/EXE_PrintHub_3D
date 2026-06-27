package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.dispute.DisputeCreateRequestDTO;
import com.fpt.printhub_3d.dto.dispute.DisputeResponseDTO;
import com.fpt.printhub_3d.dto.dispute.DisputeResolutionRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Tag(name = "Dispute APIs", description = "APIs for disputes and admin resolutions")
public interface DisputeAPI {

    @Operation(
            summary = "Create dispute",
            description = "Customer hoặc Maker khởi tạo hồ sơ khiếu nại/tranh chấp cho đơn hàng.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/api/disputes")
    ResponseEntity<ApiResponse<DisputeResponseDTO>> createDispute(
            @Valid @RequestBody DisputeCreateRequestDTO request);

    @Operation(
            summary = "Resolve dispute",
            description = "Admin phê duyệt phán quyết và ghi nhận thông tin hoàn tiền nếu có.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/api/admin/disputes/{id}/resolution")
    ResponseEntity<ApiResponse<DisputeResponseDTO>> resolveDispute(
            @PathVariable UUID id,
            @Valid @RequestBody DisputeResolutionRequestDTO request);
}
