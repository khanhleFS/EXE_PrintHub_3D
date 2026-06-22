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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
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
}
