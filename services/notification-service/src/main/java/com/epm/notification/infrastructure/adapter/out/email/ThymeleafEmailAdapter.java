package com.epm.notification.infrastructure.adapter.out.email;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.epm.notification.domain.port.out.EmailPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Email adapter that renders HTML templates via Thymeleaf and sends via {@link JavaMailSender}.
 *
 * <p>Active only when {@code notifications.email.enabled=true}.
 * The {@code templateName} parameter is the template path relative to the Thymeleaf prefix
 * (e.g. {@code "email/task-assigned-v1"} maps to {@code classpath:/templates/email/task-assigned-v1.html}).
 *
 * <p>On exception: logs at ERROR level and returns without rethrowing
 * (fire-and-forget — email failure MUST NOT break notification persistence).
 */
@Component
@ConditionalOnProperty(name = "notifications.email.enabled", havingValue = "true")
public class ThymeleafEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(ThymeleafEmailAdapter.class);
    private static final String FROM_ADDRESS = "notifications@epm.com";

    private final SpringTemplateEngine templateEngine;
    private final JavaMailSender mailSender;

    public ThymeleafEmailAdapter(SpringTemplateEngine templateEngine, JavaMailSender mailSender) {
        this.templateEngine = templateEngine;
        this.mailSender = mailSender;
    }

    @Override
    @CircuitBreaker(name = "emailAdapter", fallbackMethod = "sendFallback")
    public void send(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            String html = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(FROM_ADDRESS);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Email sent to {}: subject={}, template={}", to, subject, templateName);
        } catch (Exception e) {
            log.error("Failed to send email to {} via template {}: {}", to, templateName, e.getMessage(), e);
            // Best-effort: do NOT rethrow — email failure must not roll back notification persistence
        }
    }

    /**
     * Fallback for {@link #send} — invoked when the emailAdapter circuit breaker is OPEN.
     *
     * <p>Email is best-effort: log the circuit-open event and return without rethrowing,
     * consistent with the fire-and-forget contract of this adapter.
     */
    public void sendFallback(String to, String subject, String templateName,
                             Map<String, Object> variables, Throwable ex) {
        log.warn("Email circuit breaker OPEN — skipping email to {}: {}", to, ex.getMessage());
        // Best-effort: do NOT rethrow — email failure must not roll back notification persistence
    }
}
