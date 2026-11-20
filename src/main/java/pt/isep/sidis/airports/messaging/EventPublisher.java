package pt.isep.sidis.airports.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pt.isep.sidis.airports.cluster.ClusterConfig;
import pt.isep.sidis.airports.events.DomainEvent;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ClusterConfig clusterConfig;

    @Value("${sidis.rabbitmq.exchange:sidis.airports.exchange}")
    private String exchangeName;

    public EventPublisher(RabbitTemplate rabbitTemplate, ClusterConfig clusterConfig) {
        this.rabbitTemplate = rabbitTemplate;
        this.clusterConfig = clusterConfig;
    }

    public void publish(String routingKey, DomainEvent event) {
        if (event.getSourceInstance() == null || event.getSourceInstance().isBlank()) {
            event.setSourceInstance(clusterConfig.getSelfName());
        }

        try {
            log.info("Publishing event {} with routing key {} to exchange {}",
                    event.getEventType(), routingKey, exchangeName);
            rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
        } catch (Exception e) {
            log.warn("Could not publish event {} to RabbitMQ (broker may be offline): {}",
                    event.getEventType(), e.getMessage());
        }
    }
}
