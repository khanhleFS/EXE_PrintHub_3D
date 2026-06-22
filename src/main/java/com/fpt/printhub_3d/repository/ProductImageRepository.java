package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Product;
import com.fpt.printhub_3d.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIn(Collection<Product> products);
    List<ProductImage> findByProductId(Object productId);
}
