package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.entity.StoreSetting;
import com.fpt.printhub_3d.repository.StoreSettingRepository;
import com.fpt.printhub_3d.service.StoreSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StoreSettingServiceImpl implements StoreSettingService {

    private final StoreSettingRepository storeSettingRepository;
    private static final Set<String> ALLOWED_KEYS = Set.of("storeName", "supportEmail", "supportPhone");

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getSettings() {
        Map<String, String> result = new LinkedHashMap<>();
        storeSettingRepository.findAll().forEach(s -> result.put(s.getKey(), s.getValue()));
        return result;
    }

    @Override
    public void updateSettings(Map<String, String> settings) {
        if (settings == null) return;

        for (Map.Entry<String, String> entry : settings.entrySet()) {
            if (!ALLOWED_KEYS.contains(entry.getKey())) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Cài đặt không được hỗ trợ: " + entry.getKey());
            }
            if (entry.getValue() != null && entry.getValue().length() > 200) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Giá trị cài đặt quá dài (tối đa 200 ký tự)");
            }
            StoreSetting s = storeSettingRepository.findById(entry.getKey()).orElseGet(() -> {
                StoreSetting newS = new StoreSetting();
                newS.setKey(entry.getKey());
                return newS;
            });
            s.setValue(entry.getValue());
            storeSettingRepository.save(s);
        }
    }
}
