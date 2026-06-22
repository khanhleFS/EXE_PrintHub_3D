package com.fpt.printhub_3d.repository.specification;

import com.fpt.printhub_3d.dto.marketplace.ProductFilterDTO;
import com.fpt.printhub_3d.entity.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    private ProductSpecification() {
    }

    /**
     * Tạo Specification từ bộ lọc để tìm kiếm sản phẩm marketplace.
     */
    public static Specification<Product> withFilter(ProductFilterDTO filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Chỉ hiển thị sản phẩm đang ACTIVE
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));

            // Chỉ hiển thị sản phẩm của người bán đang hoạt động (isActive = true)
            predicates.add(cb.isTrue(root.get("seller").get("isActive")));

            // Tìm kiếm theo từ khóa (tiêu đề, mô tả, tên người bán, tên danh mục)
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String pattern = "%" + filter.keyword().trim().toLowerCase() + "%";
                Predicate titleLike = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descriptionLike = cb.like(cb.lower(root.get("description")), pattern);
                Predicate sellerNameLike = cb.like(cb.lower(root.get("seller").get("fullName")), pattern);
                Predicate categoryNameLike = cb.like(cb.lower(root.get("category").get("categoryName")), pattern);
                predicates.add(cb.or(titleLike, descriptionLike, sellerNameLike, categoryNameLike));
            }

            // Lọc theo danh mục
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("categoryId"), filter.categoryId()));
            }

            // Lọc theo loại sản phẩm (PHYSICAL/DIGITAL)
            if (filter.type() != null && !filter.type().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("type")), filter.type().trim().toUpperCase()));
            }

            // Lọc theo giá tối thiểu
            if (filter.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), BigDecimal.valueOf(filter.minPrice())));
            }

            // Lọc theo giá tối đa
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), BigDecimal.valueOf(filter.maxPrice())));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
