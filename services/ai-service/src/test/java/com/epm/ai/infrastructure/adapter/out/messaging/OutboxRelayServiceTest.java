package com.epm.ai.infrastructure.adapter.out.messaging;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OutboxRelayService}.
 *
 * <p>Verifies that the scheduler and event-listener entry points correctly delegate to
 * {@link OutboxRelayExecutor}. The transactional relay logic itself is tested in
 * {@link OutboxRelayExecutorTest}.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    private OutboxRelayExecutor executor;

    @InjectMocks
    private OutboxRelayService outboxRelayService;

    @Test
    void onOutboxEventSaved_delegatesToRelayPending() {
        outboxRelayService.onOutboxEventSaved(new OutboxEventSavedEvent(this));
        verify(executor).relayPending();
    }

    @Test
    void relayScheduled_delegatesBothRelayAndRetry() {
        outboxRelayService.relayScheduled();
        verify(executor).relayPending();
        verify(executor).retryFailed();
    }
}
