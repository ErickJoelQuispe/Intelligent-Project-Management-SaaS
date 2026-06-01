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
import com.epm.user.domain.port.out.UserProfileRepository;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AccountRegisteredConsumer}.
 */
@ExtendWith(MockitoExtension.class)
class AccountRegisteredConsumerTest {

    @Mock
    private UserProfileRepository profileRepository;

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
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(message);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getId()).isEqualTo(accountId);
        assertThat(profileCaptor.getValue().getEmail()).isEqualTo("alice@example.com");

        ArgumentCaptor<ProcessedEventJpaEntity> processedCaptor =
                ArgumentCaptor.forClass(ProcessedEventJpaEntity.class);
        verify(processedEventRepository).save(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getEventId()).isEqualTo(eventId.toString());
    }

    @Test
    void duplicateEventIsSkippedSilently() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "alice@example.com", "Alice", "Smith");

        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(true);

        consumer.consume(message);

        verify(profileRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void processedEventRepoIsCheckedBeforeCreatingProfile() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String message = buildMessage(eventId, accountId, tenantId, "bob@example.com", "Bob", "Jones");

        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(false);
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(message);

        // existsByEventId called once, then profile saved, then processedEvent saved
        verify(processedEventRepository, times(1)).existsByEventId(eventId.toString());
        verify(profileRepository, times(1)).save(any());
        verify(processedEventRepository, times(1)).save(any());
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
