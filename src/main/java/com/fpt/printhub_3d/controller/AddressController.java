package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.AddressAPI;
import com.fpt.printhub_3d.dto.address.AddressRequestDTO;
import com.fpt.printhub_3d.dto.address.AddressResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class AddressController implements AddressAPI {

    private final AddressService addressService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AddressResponseDTO>>> getMyAddresses() {
        User user = SecurityUtils.getCurrentUser();
        List<AddressResponseDTO> list = addressService.getMyAddresses(user);
        return ResponseEntity.ok(ApiResponse.<List<AddressResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách địa chỉ thành công")
                .result(list)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AddressResponseDTO>> createAddress(AddressRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        AddressResponseDTO result = addressService.createAddress(user, request);
        return ResponseEntity.ok(ApiResponse.<AddressResponseDTO>builder()
                .code(200)
                .message("Thêm địa chỉ thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AddressResponseDTO>> setDefaultAddress(Long id) {
        User user = SecurityUtils.getCurrentUser();
        AddressResponseDTO result = addressService.setDefaultAddress(id, user);
        return ResponseEntity.ok(ApiResponse.<AddressResponseDTO>builder()
                .code(200)
                .message("Đặt địa chỉ mặc định thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(Long id) {
        User user = SecurityUtils.getCurrentUser();
        addressService.deleteAddress(id, user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Xóa địa chỉ thành công")
                .build());
    }
}
