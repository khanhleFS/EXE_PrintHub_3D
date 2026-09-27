package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.address.AddressRequestDTO;
import com.fpt.printhub_3d.dto.address.AddressResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/addresses")
@Tag(name = "Address APIs", description = "APIs for user shipping addresses")
public interface AddressAPI {

    @Operation(summary = "Get user shipping addresses", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping
    ResponseEntity<ApiResponse<List<AddressResponseDTO>>> getMyAddresses();

    @Operation(summary = "Create shipping address", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PostMapping
    ResponseEntity<ApiResponse<AddressResponseDTO>> createAddress(@Valid @RequestBody AddressRequestDTO request);

    @Operation(summary = "Set default shipping address", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/{id}/default")
    ResponseEntity<ApiResponse<AddressResponseDTO>> setDefaultAddress(@PathVariable Long id);

    @Operation(summary = "Delete shipping address", security = @SecurityRequirement(name = "Bearer Authentication"))
    @DeleteMapping("/{id}")
    ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable Long id);
}
