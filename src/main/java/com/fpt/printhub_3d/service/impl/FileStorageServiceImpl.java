package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CustomPrintErrorCode;
import com.fpt.printhub_3d.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path rootLocation = Paths.get("uploads/custom-prints");

    @Override
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(CustomPrintErrorCode.INVALID_FILE);
        }

        try {
            // Create directories if they do not exist
            if (!Files.exists(rootLocation)) {
                Files.createDirectories(rootLocation);
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".stl")) {
                throw new ApiException(CustomPrintErrorCode.INVALID_FILE_EXTENSION);
            }

            // Generate unique filename to avoid conflict
            String fileExtension = ".stl";
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            Path destinationFile = this.rootLocation.resolve(Paths.get(uniqueFilename))
                    .normalize().toAbsolutePath();

            // Guard against path traversal attacks
            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                throw new ApiException(CustomPrintErrorCode.FILE_STORAGE_FAILED, "Không thể lưu tệp ngoài thư mục chỉ định");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Lưu file custom print thành công: {} -> {}", originalFilename, uniqueFilename);

            // Trả về đường dẫn tương đối để Client tải/truy cập file
            return "/uploads/custom-prints/" + uniqueFilename;
        } catch (IOException e) {
            log.error("Lỗi khi lưu file custom print", e);
            throw new ApiException(CustomPrintErrorCode.FILE_STORAGE_FAILED, "Lỗi khi lưu file: " + e.getMessage());
        }
    }
}
