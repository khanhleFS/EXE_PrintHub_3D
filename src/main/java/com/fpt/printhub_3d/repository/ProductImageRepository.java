package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Product;
import com.fpt.printhub_3d.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIn(Collection<Product> products);
    List<ProductImage> findByProductId(UUID productId);
    void deleteByProduct(Product product);
}
