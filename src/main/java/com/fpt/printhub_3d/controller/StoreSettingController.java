package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.controller.api.StoreSettingAPI;
import com.fpt.printhub_3d.service.StoreSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class StoreSettingController implements StoreSettingAPI {

    private final StoreSettingService storeSettingService;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSettings() {
        Map<String, String> result = storeSettingService.getSettings();
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .code(200)
                .message("Lấy cấu hình thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSettings(Map<String, String> settings) {
        storeSettingService.updateSettings(settings);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Cập nhật cấu hình thành công")
                .build());
    }
}
