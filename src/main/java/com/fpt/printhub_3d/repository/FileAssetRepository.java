package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.FileAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {
    List<FileAsset> findByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(UUID ownerId);
    Optional<FileAsset> findByIdAndDeletedFalse(UUID id);
}
