package com.fpt.printhub_3d.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    /**
     * Lưu trữ tệp thiết kế hình học định dạng .STL được tải lên.
     *
     * @param file tệp thiết kế hình học từ khách hàng
     * @return đường dẫn (URL) truy cập tệp sau khi lưu trữ thành công
     */
    String storeFile(MultipartFile file);
}
