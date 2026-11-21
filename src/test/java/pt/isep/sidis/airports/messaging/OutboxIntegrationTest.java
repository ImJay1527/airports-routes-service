package pt.isep.sidis.airports.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.events.AirportCreatedEvent;
import pt.isep.sidis.airports.messaging.outbox.OutboxDispatcher;
import pt.isep.sidis.airports.messaging.outbox.OutboxEvent;
import pt.isep.sidis.airports.messaging.outbox.OutboxEventRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class OutboxIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Test
    @Transactional
    void shouldEnqueueEventInOutboxTableOnPublish() {
        long countBefore = outboxEventRepository.count();

        AirportCreatedEvent event = new AirportCreatedEvent(
                "instance1", "HOR", "Horta Airport", "Faial", "Portugal", "OPERATIONAL", List.of("A320neo")
        );

        eventPublisher.publish(RabbitMQConfig.ROUTING_KEY_AIRPORT_CREATED, event);

        List<OutboxEvent> all = outboxEventRepository.findAll();
        assertThat(all.size()).isGreaterThan((int) countBefore);

        OutboxEvent last = all.get(all.size() - 1);
        assertThat(last.getRoutingKey()).isEqualTo(RabbitMQConfig.ROUTING_KEY_AIRPORT_CREATED);
        assertThat(last.getPayload()).contains("HOR");
        assertThat(last.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldHandleDispatcherRunSafelyWhenBrokerOffline() {
        outboxDispatcher.dispatchPending();
        // Dispatches without throwing uncaught exceptions even when RabbitMQ broker is offline
    }
}
