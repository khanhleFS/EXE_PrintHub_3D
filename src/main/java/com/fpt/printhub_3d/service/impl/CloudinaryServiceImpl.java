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
}
