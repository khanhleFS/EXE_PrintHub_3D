package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.entity.Category;
import com.fpt.printhub_3d.repository.CategoryRepository;
import com.fpt.printhub_3d.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin("*")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> createCategory(@RequestBody Category category) {
        if (category.getCategoryName() == null || category.getCategoryName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tên danh mục không được để trống"));
        }
        category.setCategoryName(category.getCategoryName().trim());
        if (category.getDescription() != null) {
            category.setDescription(category.getDescription().trim());
        }
        Category saved = categoryRepository.save(category);
        return ResponseEntity.ok(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody Category request) {
        Optional<Category> opt = categoryRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (request.getCategoryName() != null && request.getCategoryName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tên danh mục không được để trống"));
        }

        Category cat = opt.get();
        if (request.getCategoryName() != null) {
            cat.setCategoryName(request.getCategoryName().trim());
        }
        if (request.getDescription() != null) {
            cat.setDescription(request.getDescription().trim());
        }
        Category updated = categoryRepository.save(cat);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        Optional<Category> opt = categoryRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Không thể xóa danh mục đang có " + productCount + " sản phẩm liên kết"));
        }

        categoryRepository.delete(opt.get());
        return ResponseEntity.ok(Map.of("message", "Đã xóa danh mục thành công"));
    }
}
