package com.epm.notification.domain.port.out;

import java.util.Map;

/**
 * Output port for email delivery via HTML templates.
 *
 * <p>Implemented by the email adapter in the infrastructure layer.
 * Email is best-effort: failures MUST NOT break notification persistence.
 */
public interface EmailPort {

    /**
     * Sends an HTML email rendered from a Thymeleaf template.
     *
     * @param to           recipient email address
     * @param subject      email subject line
     * @param templateName Thymeleaf template name (without path prefix or extension),
     *                     e.g. {@code "task-assigned-v1"}
     * @param variables    template variables injected via {@code th:text}
     */
    void send(String to, String subject, String templateName, Map<String, Object> variables);
}
