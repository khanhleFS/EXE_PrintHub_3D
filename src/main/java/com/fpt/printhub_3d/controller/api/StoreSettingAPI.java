package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@RequestMapping("/api/admin/settings")
@Tag(name = "Store Setting APIs", description = "APIs for platform store settings")
public interface StoreSettingAPI {

    @Operation(
            summary = "Get store settings",
            description = "Lấy các cấu hình hệ thống (storeName, supportEmail, supportPhone).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping
    ResponseEntity<ApiResponse<Map<String, String>>> getSettings();

    @Operation(
            summary = "Update store settings",
            description = "Cập nhật các cấu hình hệ thống.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping
    ResponseEntity<ApiResponse<Void>> updateSettings(@RequestBody Map<String, String> settings);
}
