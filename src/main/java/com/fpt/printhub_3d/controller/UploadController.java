package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@CrossOrigin("*")
@Tag(name = "Upload APIs", description = "APIs for uploading media files")
public class UploadController {

    private final CloudinaryService cloudinaryService;

    @Operation(summary = "Upload image to Cloudinary", description = "Tải ảnh lên Cloudinary và nhận về URL công khai.")
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = cloudinaryService.uploadImage(file);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .code(200)
                .message("Tải ảnh lên Cloudinary thành công")
                .result(url)
                .build());
    }
}
