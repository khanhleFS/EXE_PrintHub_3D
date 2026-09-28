package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.VaultAPI;
import com.fpt.printhub_3d.dto.vault.FileAssetResponseDTO;
import com.fpt.printhub_3d.entity.FileAsset;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class VaultController implements VaultAPI {

    private final VaultService vaultService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<FileAssetResponseDTO>>> getFiles() {
        User user = SecurityUtils.getCurrentUser();
        List<FileAssetResponseDTO> files = vaultService.getMyFiles(user);
        return ResponseEntity.ok(ApiResponse.<List<FileAssetResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách tệp thành công")
                .result(files)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FileAssetResponseDTO>> uploadFile(MultipartFile file) {
        User user = SecurityUtils.getCurrentUser();
        FileAssetResponseDTO result = vaultService.uploadFile(user, file);
        return ResponseEntity.ok(ApiResponse.<FileAssetResponseDTO>builder()
                .code(200)
                .message("Tải lên tệp thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Resource> downloadFile(UUID id) {
        User user = SecurityUtils.getCurrentUser();
        FileAsset file = vaultService.getFileAsset(id, user);
        Resource resource = vaultService.downloadFile(id, user);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(resource);
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFile(UUID id) {
        User user = SecurityUtils.getCurrentUser();
        vaultService.deleteFile(id, user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Xóa tệp thành công")
                .build());
    }
}
