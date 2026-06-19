package com.epm.user.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.user.domain.model.UserProfile;
import com.epm.user.infrastructure.adapter.out.persistence.UserProfilePersistenceAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link AccountRegisteredConsumer}.
 *
 * <p>Idempotency is delegated to {@link IdempotencyGuard#claim} (REQUIRES_NEW): the guard
 * returns {@code true} when the event is claimed for the first time and {@code false} when
 * it is a benign duplicate. The consumer must skip on {@code false} and proceed on
 * {@code true}; a genuine {@code user_profiles} conflict must still propagate.
 */
@ExtendWith(MockitoExtension.class)
class AccountRegisteredConsumerTest {

    private static final String TOPIC = "auth.account.registered";

    @Mock
    private UserProfilePersistenceAdapter profileRepository;

    @Mock
    private IdempotencyGuard idempotencyGuard;

    private AccountRegisteredConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new AccountRegisteredConsumer(profileRepository, idempotencyGuard, objectMapper);
    }

    @Test
    void firstTimeEventCreatesProfile() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "alice@example.com", "Alice", "Smith");

        when(idempotencyGuard.claim(eq(eventId.toString()), eq(TOPIC))).thenReturn(true);
        when(profileRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(message);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).saveAndFlush(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getId()).isEqualTo(accountId);
        assertThat(profileCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void duplicateEventIsSkippedWhenGuardReturnsFalse() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "alice@example.com", "Alice", "Smith");

        when(idempotencyGuard.claim(eq(eventId.toString()), eq(TOPIC))).thenReturn(false);

        consumer.consume(message);

        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void claimRunsBeforeProfileSave() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "bob@example.com", "Bob", "Jones");

        when(idempotencyGuard.claim(eq(eventId.toString()), eq(TOPIC))).thenReturn(true);
        when(profileRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(message);

        verify(idempotencyGuard, times(1)).claim(eventId.toString(), TOPIC);
        verify(profileRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void malformedMessageMissingEventIdIsSkippedWithoutRethrow() {
        // A structurally invalid payload (missing eventId) is a poison message: parsing
        // happens up-front, BEFORE the claim, so the consumer must skip it without rethrowing
        // and without ever touching the guard or the repository.
        String malformed = """
                {
                  "eventType": "AccountRegistered",
                  "tenantId": "%s",
                  "payload": {
                    "accountId": "%s",
                    "email": "alice@example.com"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        // No exception escapes — poison is logged and skipped.
        consumer.consume(malformed);

        verify(idempotencyGuard, never()).claim(any(), any());
        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void genuineProfileConflictPropagates() {
        // A real user_profiles unique violation (different eventId, same tenant+email) is
        // NOT a benign idempotency duplicate: it must propagate so the message hits the DLT.
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "conflict@example.com", "C", "D");

        when(idempotencyGuard.claim(eq(eventId.toString()), eq(TOPIC))).thenReturn(true);
        when(profileRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(RuntimeException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildMessage(UUID eventId, UUID accountId, UUID tenantId,
            String email, String firstName, String lastName) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "AccountRegistered",
                  "eventVersion": 1,
                  "tenantId": "%s",
                  "payload": {
                    "accountId": "%s",
                    "tenantId": "%s",
                    "email": "%s",
                    "firstName": "%s",
                    "lastName": "%s"
                  }
                }
                """.formatted(eventId, tenantId, accountId, tenantId, email, firstName, lastName);
    }
}
