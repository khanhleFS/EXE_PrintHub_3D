package com.fpt.printhub_3d.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "File không được để trống");
        }
        try {
            log.info("Bắt đầu upload file lên Cloudinary: {}", file.getOriginalFilename());
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
            String url = (String) uploadResult.get("secure_url");
            log.info("Upload thành công lên Cloudinary, URL: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Lỗi khi upload file lên Cloudinary", e);
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Lỗi khi upload file lên Cloudinary: " + e.getMessage());
        }
    }

    private static final long MAX_RAW_FILE_SIZE = 20L * 1024 * 1024; // 20MB = 20,971,520 bytes
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of("stl", "obj", "step", "stp");

    @Override
    public String uploadRawFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Tệp 3D không được để trống (0 bytes)");
        }
        if (file.getSize() > MAX_RAW_FILE_SIZE) {
            throw new ApiException(CommonErrorCode.PAYLOAD_TOO_LARGE, "Dung lượng tệp vượt quá giới hạn cho phép (tối đa 20MB)");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Tên tệp không hợp lệ, thiếu phần mở rộng định dạng 3D");
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Định dạng tệp ." + ext + " không được hỗ trợ. Hệ thống chỉ chấp nhận: .stl, .obj, .step, .stp");
        }

        try {
            log.info("Bắt đầu upload file raw lên Cloudinary: {}, folder: {}", file.getOriginalFilename(), folder);
            String rawFolder = folder != null && !folder.isBlank() ? folder : "printhub3d/3d_models";
            String baseName = originalFilename.substring(0, originalFilename.lastIndexOf(".")).replaceAll("[^a-zA-Z0-9_-]", "_");
            if (baseName.isBlank()) {
                baseName = "model";
            }
            String publicId = baseName + "_" + System.currentTimeMillis() + "." + ext;

            Map<?, ?> params = ObjectUtils.asMap(
                    "resource_type", "raw",
                    "folder", rawFolder,
                    "public_id", publicId,
                    "use_filename", false,
                    "unique_filename", false
            );
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), params);
            String url = (String) uploadResult.get("secure_url");
            log.info("Upload file raw thành công lên Cloudinary, URL: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Lỗi khi tải file 3D raw lên Cloudinary", e);
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Không thể tải file 3D lên hệ thống đám mây: " + e.getMessage());
        }
    }
}
