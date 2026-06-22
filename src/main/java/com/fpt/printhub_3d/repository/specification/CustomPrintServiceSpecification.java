package com.fpt.printhub_3d.repository.specification;

import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceFilterDTO;
import com.fpt.printhub_3d.entity.CustomPrintService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CustomPrintServiceSpecification {

    private CustomPrintServiceSpecification() {
    }

    /**
     * Tạo Specification từ bộ lọc để tìm kiếm dịch vụ in 3D.
     * - keyword: tìm theo tên dịch vụ, mô tả, hoặc tên maker (LIKE)
     * - material: lọc theo vật liệu hỗ trợ (exact match trong collection)
     * - minPrice / maxPrice: lọc theo khoảng giá tối thiểu
     * - Chỉ hiện maker đang hoạt động (isActive = true)
     */
    public static Specification<CustomPrintService> withFilter(CustomPrintServiceFilterDTO filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Chỉ hiển thị dịch vụ của maker đang hoạt động
            predicates.add(cb.isTrue(root.get("maker").get("isActive")));

            // Tìm kiếm theo từ khóa (tên dịch vụ, mô tả, tên maker)
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String pattern = "%" + filter.keyword().trim().toLowerCase() + "%";
                Predicate serviceNameLike = cb.like(cb.lower(root.get("serviceName")), pattern);
                Predicate descriptionLike = cb.like(cb.lower(root.get("description")), pattern);
                Predicate makerNameLike = cb.like(cb.lower(root.get("maker").get("fullName")), pattern);
                predicates.add(cb.or(serviceNameLike, descriptionLike, makerNameLike));
            }

            // Lọc theo vật liệu hỗ trợ
            if (filter.material() != null && !filter.material().isBlank()) {
                Join<CustomPrintService, String> materialsJoin = root.join("supportedMaterials", JoinType.INNER);
                predicates.add(cb.equal(cb.lower(materialsJoin.as(String.class)), filter.material().trim().toLowerCase()));
            }

            // Lọc theo giá tối thiểu
            if (filter.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("minimumPrice"),
                        BigDecimal.valueOf(filter.minPrice())));
            }

            // Lọc theo giá tối đa
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minimumPrice"),
                        BigDecimal.valueOf(filter.maxPrice())));
            }

            // Tránh duplicate khi join collection
            if (query != null) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
