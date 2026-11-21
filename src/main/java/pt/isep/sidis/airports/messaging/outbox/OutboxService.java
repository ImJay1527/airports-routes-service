package pt.isep.sidis.airports.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Service to enqueue domain events into the transactional outbox table.
 * Saved within the business transaction, ensuring zero phantom events on rollback.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxRepository;
    private final OutboxDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxRepository,
                         OutboxDispatcher dispatcher,
                         ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void enqueue(String routingKey, String eventId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(eventId, eventType, routingKey, payload);
            outboxRepository.save(outboxEvent);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatcher.wakeUp();
                    }
                });
            } else {
                dispatcher.wakeUp();
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event [{}] to JSON for outbox: {}", eventId, e.getMessage());
            throw new RuntimeException("Outbox serialization error", e);
        }
    }
}
