package pt.isep.sidis.airports.messaging.outbox;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.sidis.airports.messaging.RabbitMQConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches pending outbox events asynchronously to RabbitMQ broker.
 * Implements resilient retry with at-least-once delivery guarantee, publisher confirms,
 * and confirmed event deletion.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String exchangeName;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "airports-outbox-dispatcher"));
    private final AtomicBoolean wakeUpQueued = new AtomicBoolean(false);

    public OutboxDispatcher(OutboxEventRepository outboxRepository,
                            RabbitTemplate rabbitTemplate,
                            TransactionTemplate transactionTemplate,
                            @Value("${aisafe.rabbitmq.exchange:${sidis.rabbitmq.exchange:" + RabbitMQConfig.EXCHANGE_NAME + "}}") String exchangeName,
                            @Value("${aisafe.outbox.interval-ms:1000}") long intervalMs) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.exchangeName = exchangeName;
        executor.scheduleWithFixedDelay(this::dispatchPendingSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void wakeUp() {
        if (wakeUpQueued.compareAndSet(false, true)) {
            executor.execute(() -> {
                wakeUpQueued.set(false);
                dispatchPendingSafely();
            });
        }
    }

    private void dispatchPendingSafely() {
        try {
            dispatchPending();
        } catch (Exception e) {
            log.warn("Airports outbox dispatch run failed: {}", e.getMessage());
        }
    }

    public void dispatchPending() {
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEvent> pending = outboxRepository.findTop50ByStatusOrderByIdAsc("PENDING");
            if (pending.isEmpty()) {
                return;
            }

            for (OutboxEvent event : pending) {
                try {
                    Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                            .setHeader("eventId", event.getEventId())
                            .setHeader("eventType", event.getEventType())
                            .build();

                    rabbitTemplate.invoke(operations -> {
                        operations.send(exchangeName, event.getRoutingKey(), message);
                        operations.waitForConfirmsOrDie(5000);
                        return null;
                    });

                    // Publisher confirm verified: delete row to prevent unbounded table growth
                    outboxRepository.delete(event);
                    log.info("Outbox published event [{}] ({}) to exchange [{}] with routing key [{}]",
                            event.getEventId(), event.getEventType(), exchangeName, event.getRoutingKey());
                } catch (Exception e) {
                    event.incrementRetry();
                    outboxRepository.save(event);
                    log.warn("Failed to publish outbox event [{}] to RabbitMQ: {}. Broker offline; will retry.",
                            event.getEventId(), e.getMessage());
                    break; // stop current batch if broker unreachable
                }
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
