package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.marketplace.ProductResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
}
