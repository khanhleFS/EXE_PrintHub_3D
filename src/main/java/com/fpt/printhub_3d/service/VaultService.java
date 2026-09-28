package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.vault.FileAssetResponseDTO;
import com.fpt.printhub_3d.entity.FileAsset;
import com.fpt.printhub_3d.entity.User;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface VaultService {
    List<FileAssetResponseDTO> getMyFiles(User user);
    FileAssetResponseDTO uploadFile(User user, MultipartFile file);
    Resource downloadFile(UUID id, User user);
    FileAsset getFileAsset(UUID id, User user);
    void deleteFile(UUID id, User user);
}
