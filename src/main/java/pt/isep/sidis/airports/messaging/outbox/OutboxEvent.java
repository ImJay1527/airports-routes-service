package pt.isep.sidis.airports.messaging.outbox;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Transactional Outbox Event Entity.
 * Guarantees zero phantom events on transaction rollback and resilient at-least-once delivery.
 */
@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_airports_outbox_status", columnList = "status"))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String routingKey;

    @Lob
    @Column(nullable = false, length = 10000)
    private String payload;

    @Column(nullable = false)
    private String status; // PENDING, PUBLISHED, FAILED

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(String eventId, String eventType, String routingKey, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.payload = payload;
        this.status = "PENDING";
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
