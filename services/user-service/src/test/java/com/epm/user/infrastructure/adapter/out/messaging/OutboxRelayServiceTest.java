package com.epm.user.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OutboxRelayService}.
 *
 * <p>Tests the same 3 scenarios as auth-service:
 * - Pending event is published → publishedAt set
 * - Recently failed event (2 min ago) is NOT retried
 * - Old failed event (6 min ago) IS retried
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @Mock
    private KafkaOutboxPublisher publisher;

    private OutboxRelayService service;

    @BeforeEach
    void setUp() {
        service = new OutboxRelayService(outboxRepo, publisher);
    }

    @Test
    void pendingEventIsPublishedAndPublishedAtIsSet() {
        OutboxEventJpaEntity pending = buildPending();
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(pending));
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of());
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.relayPendingEvents();

        verify(publisher).publish(anyString(), anyString(), anyString());
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
        assertThat(captor.getValue().getFailedAt()).isNull();
    }

    @Test
    void recentlyFailedEventTwoMinutesAgoIsNotRetried() {
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of());
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of()); // 2-min-old failure NOT returned

        service.relayPendingEvents();

        verify(publisher, never()).publish(anyString(), anyString(), anyString());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void oldFailedEventSixMinutesAgoIsRetried() {
        OutboxEventJpaEntity oldFailed = buildOldFailed();
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of());
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of(oldFailed));
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.relayPendingEvents();

        verify(publisher, times(1)).publish(anyString(), anyString(), anyString());
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildPending() {
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.setId(UUID.randomUUID());
        e.setAggregateId(UUID.randomUUID());
        e.setAggregateType("UserProfile");
        e.setEventType("ProfileUpdated");
        e.setTopic("user.profile.updated");
        e.setPayload("{\"userId\": \"test\"}");
        e.setCreatedAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        return e;
    }

    private OutboxEventJpaEntity buildOldFailed() {
        OutboxEventJpaEntity e = buildPending();
        e.setFailedAt(Instant.now().minus(6, ChronoUnit.MINUTES));
        e.setError("previous error");
        return e;
    }
}
