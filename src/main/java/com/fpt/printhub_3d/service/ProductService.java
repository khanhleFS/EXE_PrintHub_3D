package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.marketplace.CreateProductRequestDTO;
import com.fpt.printhub_3d.dto.marketplace.UpdateProductRequestDTO;
import com.fpt.printhub_3d.dto.marketplace.ProductFilterDTO;
import com.fpt.printhub_3d.dto.marketplace.ProductResponseDTO;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ProductService {
    /**
     * Xem danh sách các sản phẩm bán sẵn công khai trên sàn với bộ lọc, tìm kiếm và phân trang.
     *
     * @param filter bộ lọc tìm kiếm sản phẩm
     * @return trang danh sách sản phẩm DTO
     */
    Page<ProductResponseDTO> getProducts(ProductFilterDTO filter);

    /**
     * Xem chi tiết sản phẩm bán sẵn công khai trên sàn theo ID.
     *
     * @param id ID của sản phẩm
     * @return thông tin chi tiết sản phẩm DTO
     */
    ProductResponseDTO getProductById(UUID id);

    /**
     * Tạo sản phẩm bán sẵn mới trên marketplace.
     *
     * @param request thông tin sản phẩm cần tạo
     * @param sellerId ID người bán (ADMIN)
     * @return thông tin chi tiết sản phẩm DTO đã tạo
     */
    ProductResponseDTO createProduct(CreateProductRequestDTO request, UUID sellerId);

    /**
     * Cập nhật thông tin sản phẩm bán sẵn trên marketplace.
     *
     * @param id ID của sản phẩm cần cập nhật
     * @param request thông tin cập nhật sản phẩm
     * @return thông tin chi tiết sản phẩm DTO sau khi cập nhật
     */
    ProductResponseDTO updateProduct(UUID id, UpdateProductRequestDTO request);

    /**
     * Xóa vĩnh viễn sản phẩm bán sẵn trên marketplace.
     *
     * @param id ID của sản phẩm cần xóa
     */
    void deleteProduct(UUID id);
}
