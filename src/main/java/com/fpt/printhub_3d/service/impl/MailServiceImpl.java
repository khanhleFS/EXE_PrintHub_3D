package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final TemplateEngine templateEngine;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${resend.api.key:}")
    private String apiKey;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String fromEmail;

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    @Override
    public void sendPlainText(String to, String subject, String body) {
        sendApiEmail(to, subject, body, false);
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody) {
        sendApiEmail(to, subject, htmlBody, true);
    }

    @Override
    public void sendWithTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);

        String htmlContent = templateEngine.process(templateName, context);
        sendHtml(to, subject, htmlContent);
        log.info("Sent template email [{}] to: {}", templateName, to);
    }

    private void sendApiEmail(String to, String subject, String content, boolean isHtml) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY chưa được cấu hình. Bỏ qua gửi email tới: {}", to);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", fromEmail);
            payload.put("to", Collections.singletonList(to));
            payload.put("subject", subject);
            if (isHtml) {
                payload.put("html", content);
            } else {
                payload.put("text", content);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(RESEND_API_URL, request, String.class);
            log.info("Gửi mail thành công tới {} qua Resend API (Status: {})", to, response.getStatusCode());
        } catch (Exception e) {
            log.error("Lỗi khi gửi mail qua Resend HTTP API: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi gửi email: " + e.getMessage(), e);
        }
    }
}
