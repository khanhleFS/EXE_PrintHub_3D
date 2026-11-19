package com.fpt.printhub_3d.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.printhub_3d.service.MailProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ResendMailProviderClient implements MailProviderClient {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${resend.api.key:}")
    private String apiKey;

    @Value("${resend.from.email:onboarding@printhub3d.id.vn}")
    private String fromEmail;

    @Autowired
    public ResendMailProviderClient(
            @Autowired(required = false) ObjectMapper objectMapper,
            @Value("${app.email-outbox.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.email-outbox.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public MailSendResult sendHtml(String to, String subject, String htmlBody, String idempotencyKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return MailSendResult.permanent(null, "RESEND_API_KEY chưa được cấu hình");
        }
        if (to == null || to.isBlank() || !to.contains("@")) {
            return MailSendResult.permanent(422, "Địa chỉ email người nhận không hợp lệ: " + to);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey.trim());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", fromEmail);
        payload.put("to", Collections.singletonList(to.trim()));
        payload.put("subject", subject);
        payload.put("html", htmlBody);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    RESEND_API_URL,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            String providerId = extractMessageId(response.getBody());
            log.info("Resend accepted email to={} idempotencyKey={} providerId={}", to, idempotencyKey, providerId);
            return MailSendResult.accepted(providerId);
        } catch (HttpStatusCodeException ex) {
            int code = ex.getStatusCode().value();
            String body = ex.getResponseBodyAsString();
            if (code == 429 || code >= 500) {
                log.warn("Resend retryable error HTTP {} for key={}: {}", code, idempotencyKey, body);
                return MailSendResult.retryable(code, "HTTP " + code + ": " + body);
            }
            log.error("Resend permanent error HTTP {} for key={}: {}", code, idempotencyKey, body);
            return MailSendResult.permanent(code, "HTTP " + code + ": " + body);
        } catch (ResourceAccessException ex) {
            if (isReadTimeout(ex)) {
                log.warn("Resend read timeout (ambiguous delivery) for key={}: {}", idempotencyKey, ex.getMessage());
                return MailSendResult.timeoutAmbiguous("Read timeout: " + ex.getMessage());
            }
            log.warn("Resend network error (retryable) for key={}: {}", idempotencyKey, ex.getMessage());
            return MailSendResult.retryable(null, "Network error: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected Resend error for key={}: {}", idempotencyKey, ex.getMessage(), ex);
            return MailSendResult.retryable(null, "Unexpected error: " + ex.getMessage());
        }
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("id")) {
                return root.get("id").asText();
            }
        } catch (Exception ignored) {
            // giữ null nếu response không có JSON id
        }
        return null;
    }

    private boolean isReadTimeout(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
