package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.vault.FileAssetResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/vault")
@Tag(name = "File Vault APIs", description = "APIs for personal 3D file vault management")
public interface VaultAPI {

    @Operation(
            summary = "Get my uploaded files",
            description = "Lấy danh sách các tệp 3D cá nhân trong kho File Vault của người dùng hiện tại.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/files")
    ResponseEntity<ApiResponse<List<FileAssetResponseDTO>>> getFiles();

    @Operation(
            summary = "Upload 3D model file",
            description = "Tải lên tệp 3D (.stl, .obj, .step, .stp) tối đa 20MB vào kho lưu trữ cá nhân.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<FileAssetResponseDTO>> uploadFile(
            @RequestParam("file") MultipartFile file);

    @Operation(
            summary = "Download file by ID",
            description = "Tải tệp 3D từ kho cá nhân.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/files/{id}/download")
    ResponseEntity<Resource> downloadFile(@PathVariable UUID id);

    @Operation(
            summary = "Delete file from vault",
            description = "Xóa tệp khỏi kho cá nhân nếu chưa gắn với yêu cầu đặt in nào.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @DeleteMapping("/files/{id}")
    ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable UUID id);
}
