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

    /**
     * Tải file nhị phân raw (như file 3D STL, OBJ, STEP, 3MF) lên Cloudinary và trả về secure URL.
     *
     * @param file tệp nhị phân tải lên từ client
     * @param folder thư mục lưu trữ trên Cloudinary
     * @return secure URL CDN của tệp sau khi tải lên thành công
     */
    String uploadRawFile(MultipartFile file, String folder);
}
