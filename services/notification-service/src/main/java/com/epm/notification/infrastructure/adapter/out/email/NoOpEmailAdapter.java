package com.epm.notification.infrastructure.adapter.out.email;

import java.util.Map;

import com.epm.notification.domain.port.out.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op email adapter — active by default when {@code notifications.email.enabled=false} or unset.
 *
 * <p>Logs at DEBUG level and performs no action. Safe default for local development
 * and environments where no SMTP server is configured.
 */
@Component
@ConditionalOnProperty(
        name = "notifications.email.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class NoOpEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailAdapter.class);

    @Override
    public void send(String to, String subject, String templateName, Map<String, Object> variables) {
        log.debug("Email disabled — skipping send to {} subject='{}' template='{}'",
                to, subject, templateName);
    }
}
