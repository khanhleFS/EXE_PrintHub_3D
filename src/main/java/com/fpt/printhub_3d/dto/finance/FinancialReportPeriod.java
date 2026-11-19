package com.fpt.printhub_3d.dto.finance;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public record FinancialReportPeriod(Instant from, Instant to) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public FinancialReportPeriod {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Ngày bắt đầu phải trước ngày kết thúc.");
        }
    }

    public static FinancialReportPeriod parse(String from, String to) {
        return new FinancialReportPeriod(parseBoundary(from), parseBoundary(to));
    }

    public boolean includes(Instant timestamp) {
        return timestamp != null
                && (from == null || !timestamp.isBefore(from))
                && (to == null || timestamp.isBefore(to));
    }

    private static Instant parseBoundary(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value).atStartOfDay(ZONE).toInstant();
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException invalid) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT,
                        "Kỳ báo cáo dùng ngày YYYY-MM-DD hoặc thời điểm ISO-8601 có múi giờ.");
            }
        }
    }
}
