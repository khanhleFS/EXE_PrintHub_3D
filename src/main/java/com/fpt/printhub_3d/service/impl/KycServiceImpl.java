package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.repository.SystemBlacklistRepository;
import com.fpt.printhub_3d.service.KycService;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {

    private final SystemBlacklistRepository systemBlacklistRepository;

    @Override
    public Map<String, String> extractCccdData(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "URL ảnh CCCD không được để trống.");
        }

        BufferedImage bufferedImage;
        try {
            // Sử dụng URI.toURL() để tránh cảnh báo deprecation trong các phiên bản Java mới
            URL url = URI.create(imageUrl).toURL();
            bufferedImage = ImageIO.read(url);
            if (bufferedImage == null) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Không thể đọc hình ảnh từ URL cung cấp.");
            }
        } catch (Exception e) {
            log.error("Lỗi khi tải ảnh từ URL: {}", imageUrl, e);
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Không tải được hình ảnh từ URL: " + e.getMessage());
        }

        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            Result result = new MultiFormatReader().decode(bitmap);
            String rawData = result.getText();
            log.info("Đã giải mã QR Code thành công.");

            Map<String, String> dataMap = parseCccdRawData(rawData);
            String cccdNumber = dataMap.get("cccdNumber");

            if (cccdNumber == null || cccdNumber.trim().isEmpty()) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Mã QR không đúng cấu trúc CCCD Việt Nam.");
            }

            // Kiểm tra Blacklist
            if (systemBlacklistRepository.existsByCccdNumber(cccdNumber)) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Số căn cước công dân này (" + cccdNumber + ") nằm trong danh sách đen của hệ thống.");
            }

            return dataMap;
        } catch (NotFoundException e) {
            log.warn("Không tìm thấy mã QR trong ảnh: {}", imageUrl);
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Không tìm thấy mã QR hợp lệ trong ảnh. Vui lòng đảm bảo ảnh chụp rõ nét, vuông góc và không bị lóa đèn.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi xử lý quét ảnh CCCD", e);
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Lỗi hệ thống khi quét ảnh CCCD: " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> parseCccdRawData(String rawData) {
        String[] parts = rawData.split("\\|");
        Map<String, String> dataMap = new HashMap<>();

        if (parts.length >= 6) {
            dataMap.put("cccdNumber", parts[0]);
            dataMap.put("fullName", parts[2]);
            dataMap.put("dob", parts[3]);
            dataMap.put("gender", parts[4]);
            dataMap.put("address", parts[5]);
        } else if (parts.length >= 1) {
            dataMap.put("cccdNumber", parts[0]);
        }
        return dataMap;
    }

    // Implement the methods defined in KycService interface here
}
