package com.epm.user.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.epm.user.domain.model.UserProfile;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
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
 * <p>Covers the TOCTOU-safe idempotency logic: fast-path existsByEventId for the
 * common case, and DataIntegrityViolationException catch for the concurrent race.
 */
@ExtendWith(MockitoExtension.class)
class AccountRegisteredConsumerTest {

    @Mock
    private UserProfilePersistenceAdapter profileRepository;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    private AccountRegisteredConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new AccountRegisteredConsumer(profileRepository, processedEventRepository, objectMapper);
    }

    @Test
    void firstTimeEventCreatesProfileAndSavesProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "alice@example.com", "Alice", "Smith");

        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(message);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).saveAndFlush(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getId()).isEqualTo(accountId);
        assertThat(profileCaptor.getValue().getEmail()).isEqualTo("alice@example.com");

        ArgumentCaptor<ProcessedEventJpaEntity> processedCaptor =
                ArgumentCaptor.forClass(ProcessedEventJpaEntity.class);
        verify(processedEventRepository).saveAndFlush(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getEventId()).isEqualTo(eventId.toString());
    }

    @Test
    void duplicateEventIsSkippedSilentlyViaFastPath() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "alice@example.com", "Alice", "Smith");

        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(true);

        consumer.consume(message);

        verify(profileRepository, never()).saveAndFlush(any());
        verify(processedEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateIsSkippedWhenSaveAndFlushThrowsDataIntegrityViolation() {
        // Simulates the race: fast-path check passes (returns false) for BOTH threads,
        // but the second thread hits the PK constraint on saveAndFlush.
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "race@example.com", "Race", "Test");

        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        // Must NOT throw — the message should be silently acked (no DLT)
        consumer.consume(message);

        // Profile must NOT be created (we returned before reaching profileRepository.saveAndFlush)
        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void processedEventSavedBeforeProfile() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "bob@example.com", "Bob", "Jones");

        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(message);

        // Both must be called exactly once
        verify(processedEventRepository, times(1)).saveAndFlush(any());
        verify(profileRepository, times(1)).saveAndFlush(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildMessage(UUID eventId, UUID accountId, UUID tenantId,
            String email, String firstName, String lastName) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "AccountRegistered",
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
