package pt.isep.sidis.airports.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Base domain event for all AMQP event broadcasting.
 * Serialized as plain JSON without __TypeId__ or Java class metadata.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class DomainEvent implements Serializable {

    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt = Instant.now();
    private String sourceInstance;

    protected DomainEvent(String eventType, String sourceInstance) {
        this.eventType = eventType;
        this.sourceInstance = sourceInstance;
        this.occurredAt = Instant.now();
        this.eventId = UUID.randomUUID().toString();
    }
}
