package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.controller.api.CustomPrintAPI;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceFilterDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.CustomPrintManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import com.fpt.printhub_3d.service.CustomOrderService;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/custom-prints")
@RequiredArgsConstructor
public class CustomPrintController implements CustomPrintAPI {

    private final CustomPrintManagementService customPrintManagementService;
    private final CustomOrderService customOrderService;

    @Override
    @PreAuthorize("hasRole('MAKER')")
    public ResponseEntity<ApiResponse<CustomPrintServiceResponseDTO>> createService(
            CustomPrintServiceRequestDTO request) {

        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        User maker = userDetail.getUser();

        CustomPrintServiceResponseDTO response = customPrintManagementService.createService(request, maker);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<CustomPrintServiceResponseDTO>builder()
                        .code(201)
                        .message("Tạo gói dịch vụ in thành công")
                        .result(response)
                        .build());
    }

    @Override
    public ResponseEntity<ApiResponse<Page<CustomPrintServiceResponseDTO>>> getServices(
            String keyword, String material, Double minPrice, Double maxPrice,
            String sortBy, String sortDirection, Integer page, Integer size) {

        CustomPrintServiceFilterDTO filter = new CustomPrintServiceFilterDTO(
                keyword, material, minPrice, maxPrice, sortBy, sortDirection, page, size
            );

        Page<CustomPrintServiceResponseDTO> result = customPrintManagementService.getServices(filter);

        return ResponseEntity.ok(ApiResponse.<Page<CustomPrintServiceResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách dịch vụ in thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CustomOrderResponseDTO>> createCustomOrderRequest(
            UUID makerId, String requirements, MultipartFile file) {

        log.info("Nhận yêu cầu in custom từ customer gửi tới maker: {}", makerId);

        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        User buyer = userDetail.getUser();

        CustomOrderResponseDTO response = customOrderService.createRequest(makerId, requirements, file, buyer);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<CustomOrderResponseDTO>builder()
                        .code(201)
                        .message("Khởi tạo yêu cầu gia công in custom thành công")
                        .result(response)
                        .build());
    }
}
