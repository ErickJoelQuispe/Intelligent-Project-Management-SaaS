package com.epm.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Properties;

import com.epm.notification.infrastructure.adapter.out.email.ThymeleafEmailAdapter;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Unit tests for ThymeleafEmailAdapter (TDD — Strict RED→GREEN→REFACTOR).
 *
 * <p>Uses a real {@link SpringTemplateEngine} wired to classpath templates
 * and a mocked {@link JavaMailSender} to verify behaviour without an SMTP server.
 */
@ExtendWith(MockitoExtension.class)
class ThymeleafEmailAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    private SpringTemplateEngine templateEngine;
    private ThymeleafEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        // Wire a real Thymeleaf engine pointed at classpath templates (no prefix — full path passed)
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode(TemplateMode.HTML);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        adapter = new ThymeleafEmailAdapter(templateEngine, mailSender);
    }

    // ── Successful send calls JavaMailSender.send() once ──────────────────

    @Test
    void send_callsMailSenderOnce() {
        // Provide a real MimeMessage backed by an empty mail session
        MimeMessage realMimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage);

        Map<String, Object> vars = Map.of(
                "assigneeName", "Alice",
                "taskTitle", "Fix login",
                "projectName", "Alpha",
                "taskUrl", "http://example.com/task/1");

        adapter.send("alice@example.com", "Task Assigned", "templates/email/task-assigned-v1", vars);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    // ── Template rendering tests (using engine directly — no mail infra needed) ──

    @Test
    void send_rendersTaskAssignedTemplate_withAllVariables() {
        Map<String, Object> vars = Map.of(
                "assigneeName", "Bob",
                "taskTitle", "Implement feature",
                "projectName", "Beta",
                "taskUrl", "http://example.com/task/42");

        Context context = new Context();
        context.setVariables(vars);
        String rendered = templateEngine.process("templates/email/task-assigned-v1", context);

        assertThat(rendered).contains("Bob");
        assertThat(rendered).contains("Implement feature");
        assertThat(rendered).contains("Beta");
        assertThat(rendered).contains("http://example.com/task/42");
    }

    @Test
    void send_rendersTaskCreatedTemplate_withAllVariables() {
        Map<String, Object> vars = Map.of(
                "creatorName", "Charlie",
                "taskTitle", "New task",
                "projectName", "Gamma");

        Context context = new Context();
        context.setVariables(vars);
        String rendered = templateEngine.process("templates/email/task-created-v1", context);

        assertThat(rendered).contains("Charlie");
        assertThat(rendered).contains("New task");
        assertThat(rendered).contains("Gamma");
    }

    @Test
    void send_rendersProjectCreatedTemplate_withAllVariables() {
        Map<String, Object> vars = Map.of(
                "ownerName", "Diana",
                "projectName", "Delta");

        Context context = new Context();
        context.setVariables(vars);
        String rendered = templateEngine.process("templates/email/project-created-v1", context);

        assertThat(rendered).contains("Diana");
        assertThat(rendered).contains("Delta");
    }

    @Test
    void send_rendersMemberJoinedTemplate_withAllVariables() {
        Map<String, Object> vars = Map.of(
                "memberName", "Eve",
                "teamName", "Backend Team",
                "projectName", "Epsilon");

        Context context = new Context();
        context.setVariables(vars);
        String rendered = templateEngine.process("templates/email/member-joined-v1", context);

        assertThat(rendered).contains("Eve");
        assertThat(rendered).contains("Backend Team");
        assertThat(rendered).contains("Epsilon");
    }

    // ── JavaMailSender failure is swallowed (fire-and-forget) ─────────────

    @Test
    void send_whenMailSenderThrows_doesNotRethrow() {
        MimeMessage realMimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage);
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        Map<String, Object> vars = Map.of(
                "assigneeName", "Frank",
                "taskTitle", "Broken",
                "projectName", "Omega",
                "taskUrl", "http://example.com");

        // Should not rethrow
        adapter.send("frank@example.com", "Task Assigned", "templates/email/task-assigned-v1", vars);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
