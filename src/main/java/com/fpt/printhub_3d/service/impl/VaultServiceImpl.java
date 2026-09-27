package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.VaultErrorCode;
import com.fpt.printhub_3d.dto.vault.FileAssetResponseDTO;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.FileAsset;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.FileAssetRepository;
import com.fpt.printhub_3d.service.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VaultServiceImpl implements VaultService {

    private final FileAssetRepository fileAssetRepository;
    private final CustomOrderRepository customOrderRepository;

    private final Path rootDir = Paths.get("private-files").toAbsolutePath().normalize();

    private void ensureStorageDirectory() {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            log.error("Failed to create storage directory", e);
            throw new ApiException(VaultErrorCode.FILE_STORAGE_ERROR);
        }
    }

    private void checkOwnership(FileAsset file, User user) {
        if (user.getRole() != UserRole.ADMIN && !file.getOwner().getId().equals(user.getId())) {
            throw new ApiException(VaultErrorCode.FORBIDDEN_FILE_ACCESS);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileAssetResponseDTO> getMyFiles(User user) {
        return fileAssetRepository.findByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(f -> FileAssetResponseDTO.builder()
                        .id(f.getId())
                        .fileName(f.getFileName())
                        .sizeBytes(f.getSizeBytes())
                        .createdAt(f.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public FileAssetResponseDTO uploadFile(User user, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(VaultErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new ApiException(VaultErrorCode.FILE_TOO_LARGE);
        }

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "model.stl")
                .replace('\\', '/');
        originalName = originalName.substring(originalName.lastIndexOf('/') + 1);

        if (!originalName.toLowerCase().matches(".*\\.(stl|obj|step|stp)$")) {
            throw new ApiException(VaultErrorCode.INVALID_FILE_TYPE);
        }

        ensureStorageDirectory();

        String storageName = UUID.randomUUID().toString();
        Path targetPath = rootDir.resolve(storageName);

        try (var in = file.getInputStream()) {
            Files.copy(in, targetPath);
        } catch (IOException e) {
            log.error("Error storing uploaded file", e);
            throw new ApiException(VaultErrorCode.FILE_STORAGE_ERROR);
        }

        FileAsset fileAsset = new FileAsset();
        fileAsset.setOwner(user);
        fileAsset.setFileName(originalName);
        fileAsset.setSizeBytes(file.getSize());
        fileAsset.setStorageName(storageName);
        fileAsset.setCreatedAt(Instant.now());
        fileAsset.setDeleted(false);

        fileAssetRepository.save(fileAsset);

        return FileAssetResponseDTO.builder()
                .id(fileAsset.getId())
                .fileName(fileAsset.getFileName())
                .sizeBytes(fileAsset.getSizeBytes())
                .createdAt(fileAsset.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FileAsset getFileAsset(UUID id, User user) {
        FileAsset file = fileAssetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(VaultErrorCode.FILE_NOT_FOUND));
        checkOwnership(file, user);
        return file;
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadFile(UUID id, User user) {
        FileAsset file = getFileAsset(id, user);
        Path filePath = rootDir.resolve(file.getStorageName());
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists() || !resource.isReadable()) {
            throw new ApiException(VaultErrorCode.FILE_NOT_FOUND);
        }
        return resource;
    }

    @Override
    public void deleteFile(UUID id, User user) {
        FileAsset file = getFileAsset(id, user);
        String downloadUrl = "/api/vault/files/" + id + "/download";
        long refCount = customOrderRepository.countByAttachmentUrl(downloadUrl);
        if (refCount > 0) {
            throw new ApiException(VaultErrorCode.FILE_IN_USE);
        }
        file.setDeleted(true);
        fileAssetRepository.save(file);
    }
}
