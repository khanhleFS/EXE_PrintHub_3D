package com.fpt.printhub_3d.service;

/**
 * Abstraction bao bọc cổng gửi email (Resend HTTP API) để hỗ trợ Idempotency-Key,
 * timeout rõ ràng và mock được toàn bộ kịch bản 429/5xx/timeout/permanent error trong kiểm thử (EM-08..10).
 */
public interface MailProviderClient {

    enum Outcome {
        ACCEPTED,
        RETRYABLE_ERROR,
        TIMEOUT_AMBIGUOUS,
        PERMANENT_ERROR
    }

    record MailSendResult(
            Outcome outcome,
            String providerMessageId,
            Integer httpStatus,
            String errorMessage
    ) {
        public static MailSendResult accepted(String providerMessageId) {
            return new MailSendResult(Outcome.ACCEPTED, providerMessageId, 200, null);
        }

        public static MailSendResult retryable(Integer httpStatus, String errorMessage) {
            return new MailSendResult(Outcome.RETRYABLE_ERROR, null, httpStatus, errorMessage);
        }

        public static MailSendResult timeoutAmbiguous(String errorMessage) {
            return new MailSendResult(Outcome.TIMEOUT_AMBIGUOUS, null, null, errorMessage);
        }

        public static MailSendResult permanent(Integer httpStatus, String errorMessage) {
            return new MailSendResult(Outcome.PERMANENT_ERROR, null, httpStatus, errorMessage);
        }
    }

    MailSendResult sendHtml(String to, String subject, String htmlBody, String idempotencyKey);
}
