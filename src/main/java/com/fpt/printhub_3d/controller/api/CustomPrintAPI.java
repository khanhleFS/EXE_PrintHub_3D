package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceFilterDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RequestMapping("/api/custom-prints")
@Tag(name = "Custom Print Service APIs", description = "APIs for managing custom 3D printing services")
public interface CustomPrintAPI {

    @Operation(
            summary = "Create custom print service",
            description = "Maker thiết lập thông số kỹ thuật xưởng in (máy móc, loại nhựa PLA/Resin, đơn giá tối thiểu) để đưa gói dịch vụ lên chợ Custom.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/services")
    ResponseEntity<ApiResponse<CustomPrintServiceResponseDTO>> createService(
            @Valid @RequestBody CustomPrintServiceRequestDTO request);

    @Operation(
            summary = "Browse custom print services",
            description = "Khách hàng duyệt, tìm kiếm và chọn bộ lọc danh sách các xưởng in/Makers đang hoạt động. Không yêu cầu đăng nhập."
    )
    @GetMapping("/services")
    ResponseEntity<ApiResponse<Page<CustomPrintServiceResponseDTO>>> getServices(
            @Parameter(description = "Từ khóa tìm kiếm (tên dịch vụ, mô tả, tên maker)")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Lọc theo vật liệu hỗ trợ")
            @RequestParam(required = false) String material,

            @Parameter(description = "Giá tối thiểu (VND)")
            @RequestParam(required = false) Double minPrice,

            @Parameter(description = "Giá tối đa (VND)")
            @RequestParam(required = false) Double maxPrice,

            @Parameter(description = "Sắp xếp theo trường (minimumPrice, estimatedProductionDays, createdAt, serviceName)")
            @RequestParam(required = false) String sortBy,

            @Parameter(description = "Thứ tự sắp xếp (asc/desc)")
            @RequestParam(required = false) String sortDirection,

            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(required = false, defaultValue = "0") Integer page,

            @Parameter(description = "Kích thước trang")
            @RequestParam(required = false, defaultValue = "10") Integer size
    );

    @Operation(
            summary = "Create custom print request",
            description = "Khách hàng khởi tạo yêu cầu gia công đặt in Custom đặc thù, bắt buộc đính kèm tệp file thiết kế hình học định dạng .STL.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping(value = "/requests", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<CustomOrderResponseDTO>> createCustomOrderRequest(
            @Parameter(description = "ID của Maker nhận yêu cầu")
            @RequestParam("makerId") UUID makerId,

            @Parameter(description = "Mô tả chi tiết yêu cầu gia công (vật liệu, màu sắc, độ phân giải...)")
            @RequestParam("requirements") String requirements,

            @Parameter(description = "Tệp file thiết kế hình học định dạng .STL")
            @RequestParam("file") MultipartFile file
    );
}
