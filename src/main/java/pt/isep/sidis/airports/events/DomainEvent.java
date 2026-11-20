package pt.isep.sidis.airports.events;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Base domain event for all asynchronous AMQP event broadcasting.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@type")
public abstract class DomainEvent implements Serializable {

    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private Instant occurredAt = Instant.now();
    private String sourceInstance;

    protected DomainEvent(String eventType, String sourceInstance) {
        this.eventType = eventType;
        this.sourceInstance = sourceInstance;
        this.occurredAt = Instant.now();
        this.eventId = UUID.randomUUID().toString();
    }
}
