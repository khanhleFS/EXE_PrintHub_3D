package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.marketplace.ProductFilterDTO;
import com.fpt.printhub_3d.dto.marketplace.ProductResponseDTO;
import org.springframework.data.domain.Page;

public interface ProductService {
    /**
     * Xem danh sách các sản phẩm bán sẵn công khai trên sàn với bộ lọc, tìm kiếm và phân trang.
     *
     * @param filter bộ lọc tìm kiếm sản phẩm
     * @return trang danh sách sản phẩm DTO
     */
    Page<ProductResponseDTO> getProducts(ProductFilterDTO filter);
}
