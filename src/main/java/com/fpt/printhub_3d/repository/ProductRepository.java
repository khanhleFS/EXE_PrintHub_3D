package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Product e where e.id = :id")
    java.util.Optional<Product> findLockedById(@org.springframework.data.repository.query.Param("id") java.util.UUID id);

}
