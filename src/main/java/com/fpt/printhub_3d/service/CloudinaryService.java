package com.fpt.printhub_3d.service;

import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {
    /**
     * Tải file ảnh lên Cloudinary và trả về secure URL.
     *
     * @param file file ảnh tải lên từ client
     * @return secure URL của ảnh sau khi tải lên thành công
     */
    String uploadImage(MultipartFile file);
}
