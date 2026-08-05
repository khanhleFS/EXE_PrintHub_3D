package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.controller.api.ProductAPI;
import com.fpt.printhub_3d.dto.marketplace.ProductFilterDTO;
import com.fpt.printhub_3d.dto.marketplace.ProductResponseDTO;
import com.fpt.printhub_3d.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import com.fpt.printhub_3d.dto.marketplace.CreateProductRequestDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ProductController implements ProductAPI {

    private final ProductService productService;

    @Override
    public ResponseEntity<ApiResponse<Page<ProductResponseDTO>>> getProducts(
            String keyword, Long categoryId, String type, Double minPrice, Double maxPrice,
            String sortBy, String sortDirection, Integer page, Integer size) {

        ProductFilterDTO filter = new ProductFilterDTO(
                keyword, categoryId, type, minPrice, maxPrice, sortBy, sortDirection, page, size
        );

        Page<ProductResponseDTO> result = productService.getProducts(filter);

        return ResponseEntity.ok(ApiResponse.<Page<ProductResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách sản phẩm thành công")
                .result(result)
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<ProductResponseDTO>> getProductById(UUID id) {
        ProductResponseDTO result = productService.getProductById(id);

        return ResponseEntity.ok(ApiResponse.<ProductResponseDTO>builder()
                .code(200)
                .message("Lấy thông tin chi tiết sản phẩm thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> createProduct(CreateProductRequestDTO request) {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        com.fpt.printhub_3d.common.security.CustomUserDetail userDetail =
                (com.fpt.printhub_3d.common.security.CustomUserDetail) authentication.getPrincipal();
        UUID sellerId = userDetail.getUser().getId();

        ProductResponseDTO result = productService.createProduct(request, sellerId);

        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.<ProductResponseDTO>builder()
                        .code(201)
                        .message("Tạo sản phẩm thành công")
                        .result(result)
                        .build());
    }
}
