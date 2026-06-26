package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.repository.SystemBlacklistRepository;
import com.fpt.printhub_3d.service.impl.KycServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KycServiceTest {

    private KycServiceImpl kycService;

    @Mock
    private SystemBlacklistRepository systemBlacklistRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        kycService = new KycServiceImpl(systemBlacklistRepository);
    }

    @Test
    void testParseCccdRawData_Success() {
        String rawData = "038095011037||Nguyen Van A|15031995|Nam|Thon 1, Dong Son, Chuong My, Ha Noi|10122021";
        Map<String, String> data = kycService.parseCccdRawData(rawData);

        assertNotNull(data);
        assertEquals("038095011037", data.get("cccdNumber"));
        assertEquals("Nguyen Van A", data.get("fullName"));
        assertEquals("15031995", data.get("dob"));
        assertEquals("Nam", data.get("gender"));
        assertEquals("Thon 1, Dong Son, Chuong My, Ha Noi", data.get("address"));
    }

    @Test
    void testParseCccdRawData_ShortData() {
        String rawData = "038095011037";
        Map<String, String> data = kycService.parseCccdRawData(rawData);

        assertNotNull(data);
        assertEquals("038095011037", data.get("cccdNumber"));
    }
}
