package com.fpt.printhub_3d.service;

import java.util.Map;

public interface StoreSettingService {
    Map<String, String> getSettings();
    void updateSettings(Map<String, String> settings);
}
