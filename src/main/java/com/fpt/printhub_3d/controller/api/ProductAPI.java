package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.marketplace.ProductResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import com.fpt.printhub_3d.dto.marketplace.CreateProductRequestDTO;

import java.util.UUID;

@RequestMapping("/api/marketplace")
@Tag(name = "Marketplace Product APIs", description = "APIs for browsing and searching marketplace products")
public interface ProductAPI {

    @Operation(
            summary = "Get list of marketplace products",
            description = "Khách hàng xem danh sách các sản phẩm bán sẵn bình thường công khai trên sàn (như cuộn nhựa in, dung dịch Resin, máy in cũ hoặc mô hình thành phẩm in sẵn) mà không cần qua luồng Custom thiết kế. Không yêu cầu đăng nhập."
    )
    @GetMapping("/product")
    ResponseEntity<ApiResponse<Page<ProductResponseDTO>>> getProducts(
            @Parameter(description = "Từ khóa tìm kiếm (tiêu đề, mô tả, tên người bán, danh mục)")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "ID của danh mục sản phẩm")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Loại sản phẩm (PHYSICAL, DIGITAL)")
            @RequestParam(required = false) String type,

            @Parameter(description = "Giá tối thiểu")
            @RequestParam(required = false) Double minPrice,

            @Parameter(description = "Giá tối đa")
            @RequestParam(required = false) Double maxPrice,

            @Parameter(description = "Sắp xếp theo trường (price, createdAt, title)")
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Thứ tự sắp xếp (asc/desc)")
            @RequestParam(required = false, defaultValue = "desc") String sortDirection,

            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(required = false, defaultValue = "0") Integer page,

            @Parameter(description = "Kích thước trang")
            @RequestParam(required = false, defaultValue = "12") Integer size
    );

    @Operation(
            summary = "Get marketplace product details",
            description = "Xem chi tiết một sản phẩm công khai trên marketplace (bao gồm mô tả, hình ảnh, thông số kỹ thuật và số lượng tồn kho) bằng ID. Không yêu cầu đăng nhập."
    )
    @GetMapping("/product/{id}")
    ResponseEntity<ApiResponse<ProductResponseDTO>> getProductById(
            @Parameter(description = "ID của sản phẩm")
            @PathVariable UUID id
    );

    @Operation(
            summary = "Create a new marketplace product",
            description = "ADMIN khởi tạo và đăng bán mặt hàng in ấn hoặc tệp mô hình số hóa mới."
    )
    @PostMapping("/product")
    ResponseEntity<ApiResponse<ProductResponseDTO>> createProduct(
            @RequestBody @Valid CreateProductRequestDTO request
    );
}
