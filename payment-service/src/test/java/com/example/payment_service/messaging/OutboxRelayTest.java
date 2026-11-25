package com.example.payment_service.messaging;

import com.example.payment_service.entity.OutboxEvent;
import com.example.payment_service.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private EventPublisher eventPublisher;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRelay = new OutboxRelay(outboxEventRepository, eventPublisher, 50);
    }

    @Test
    void publishPending_whenPublisherNotConfigured_shouldReturnZero() {
        when(eventPublisher.isConfigured()).thenReturn(false);

        int published = outboxRelay.publishPending();

        assertThat(published).isZero();
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void publishPending_whenNoPendingEvents_shouldReturnZero() {
        when(eventPublisher.isConfigured()).thenReturn(true);
        when(outboxEventRepository.lockPending(anyInt())).thenReturn(List.of());

        int published = outboxRelay.publishPending();

        assertThat(published).isZero();
    }

    @Test
    void publishPending_whenEventsExist_shouldPublishAndMarkPublished() {
        when(eventPublisher.isConfigured()).thenReturn(true);
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "payment.succeeded", UUID.randomUUID(), Map.of("key", "val"));
        when(outboxEventRepository.lockPending(anyInt())).thenReturn(List.of(event));

        int published = outboxRelay.publishPending();

        assertThat(published).isEqualTo(1);
        assertThat(event.isPending()).isFalse();
        verify(eventPublisher).publish(eq("payment.succeeded"), any());
        verify(outboxEventRepository).save(event);
    }

    @Test
    void publishPending_whenPublishFails_shouldStopAndNotMarkPublished() {
        when(eventPublisher.isConfigured()).thenReturn(true);
        OutboxEvent event1 = new OutboxEvent(UUID.randomUUID(), "payment.succeeded", UUID.randomUUID(), Map.of("key", "1"));
        OutboxEvent event2 = new OutboxEvent(UUID.randomUUID(), "payment.succeeded", UUID.randomUUID(), Map.of("key", "2"));
        when(outboxEventRepository.lockPending(anyInt())).thenReturn(List.of(event1, event2));
        doThrow(new RuntimeException("Broker unreachable")).when(eventPublisher).publish(any(), any());

        int published = outboxRelay.publishPending();

        assertThat(published).isZero();
        assertThat(event1.isPending()).isTrue();
        verify(outboxEventRepository, never()).save(any());
    }
}
