package com.epm.notification.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.epm.notification.infrastructure.adapter.out.email.MailhogEmailAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Unit tests for MailhogEmailAdapter (T-C-07).
 */
@ExtendWith(MockitoExtension.class)
class MailhogEmailAdapterTest {

    @Mock
    private JavaMailSender javaMailSender;

    private MailhogEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MailhogEmailAdapter(javaMailSender, true);
    }

    // ── T-C-07: sendEmail delegates to JavaMailSender ─────────────────────

    @Test
    void sendEmail_whenEnabled_sendsEmailViaJavaMailSender() {
        adapter.sendEmail("user@example.com", "Task Assigned", "A task was assigned to you.");

        verify(javaMailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmail_whenDisabled_doesNotSendEmail() {
        MailhogEmailAdapter disabledAdapter = new MailhogEmailAdapter(javaMailSender, false);

        disabledAdapter.sendEmail("user@example.com", "Task Assigned", "A task was assigned to you.");

        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ── T-C-07: email send failure does not throw (best-effort) ───────────

    @Test
    void sendEmail_whenMailSenderThrows_doesNotPropagateException() {
        doThrow(new RuntimeException("SMTP unavailable")).when(javaMailSender).send(any(SimpleMailMessage.class));

        // Should not throw — email is best-effort
        adapter.sendEmail("user@example.com", "Task Assigned", "A task was assigned to you.");
    }
}
