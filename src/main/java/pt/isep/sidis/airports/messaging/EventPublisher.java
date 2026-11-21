package pt.isep.sidis.airports.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.isep.sidis.airports.cluster.ClusterConfig;
import pt.isep.sidis.airports.events.DomainEvent;
import pt.isep.sidis.airports.messaging.outbox.OutboxService;

/**
 * Domain Event Publisher implementing the Transactional Outbox Pattern.
 * Enqueues domain events within the caller's database transaction so:
 * - Rolled back transactions never produce phantom events.
 * - Outages of the message broker never lose events.
 * - Events are dispatched asynchronously after transaction commit with publisher confirms.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final OutboxService outboxService;
    private final ClusterConfig clusterConfig;

    public EventPublisher(OutboxService outboxService, ClusterConfig clusterConfig) {
        this.outboxService = outboxService;
        this.clusterConfig = clusterConfig;
    }

    public void publish(String routingKey, DomainEvent event) {
        if (event == null) return;

        if (event.getSourceInstance() == null) {
            event.setSourceInstance(clusterConfig.getSelfName());
        }

        log.info("Enqueuing domain event '{}' with routing key '{}' via Transactional Outbox",
                event.getEventType(), routingKey);

        outboxService.enqueue(routingKey, event.getEventId(), event.getEventType(), event);
    }
}
