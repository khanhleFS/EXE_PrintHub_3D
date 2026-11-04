package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.service.StoreSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public")
@CrossOrigin("*")
@Tag(name = "Public APIs", description = "Public endpoints accessible without authentication")
public class PublicSettingController {

    private final StoreSettingService storeSettingService;

    @Operation(summary = "Get public store settings", description = "Lấy cấu hình hiển thị công khai (hotline, storeName, supportEmail, supportHours)")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPublicSettings() {
        Map<String, String> result = storeSettingService.getPublicSettings();
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .code(200)
                .message("Lấy cấu hình công khai thành công")
                .result(result)
                .build());
    }
}
