package pt.isep.sidis.airports.cqrs.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.events.FlightRouteCreatedEvent;
import pt.isep.sidis.airports.events.FlightRouteDeactivatedEvent;
import pt.isep.sidis.airports.events.FlightRouteUpdatedEvent;
import pt.isep.sidis.airports.flightroutes.api.dto.FlightRouteResponseDTO;
import pt.isep.sidis.airports.flightroutes.services.FlightRouteService;
import pt.isep.sidis.airports.messaging.EventPublisher;

/**
 * CQRS Command Handler for Flight Route write operations.
 * Enforces business rules and publishes domain events to RabbitMQ via Transactional Outbox.
 */
@Service
public class FlightRouteCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(FlightRouteCommandHandler.class);

    private final FlightRouteService flightRouteService;
    private final EventPublisher eventPublisher;

    public FlightRouteCommandHandler(FlightRouteService flightRouteService, EventPublisher eventPublisher) {
        this.flightRouteService = flightRouteService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FlightRouteResponseDTO handle(CreateFlightRouteCommand command) {
        log.info("CQRS Command: Creating flight route from {} to {}",
                command.getRequestDTO().getOriginIata(),
                command.getRequestDTO().getDestinationIata());

        FlightRouteResponseDTO created = flightRouteService.createFlightRoute(command.getRequestDTO());

        FlightRouteCreatedEvent event = new FlightRouteCreatedEvent(
                null,
                created.getRouteId(),
                command.getRequestDTO().getOriginIata(),
                command.getRequestDTO().getDestinationIata(),
                created.getDistance(),
                command.getRequestDTO().getMinRangeRequired(),
                command.getRequestDTO().getMinCapacityRequired(),
                created.getEstimatedFlightTime(),
                created.getRouteStatus() != null ? created.getRouteStatus().name() : "ACTIVE"
        );

        eventPublisher.publish("route.created", event);
        return created;
    }

    @Transactional
    public FlightRouteResponseDTO handle(UpdateFlightRouteCommand command) {
        log.info("CQRS Command: Updating flight route {}", command.getRouteId());
        FlightRouteResponseDTO updated = flightRouteService.updateRoute(command.getRouteId(), command.getDto());

        FlightRouteUpdatedEvent event = new FlightRouteUpdatedEvent(
                null,
                updated.getRouteId(),
                updated.getOriginIataCode(),
                updated.getDestinationIataCode(),
                updated.getDistance(),
                command.getDto().getMinRangeRequired(),
                command.getDto().getMinCapacityRequired(),
                updated.getEstimatedFlightTime(),
                updated.getRouteStatus() != null ? updated.getRouteStatus().name() : "ACTIVE"
        );

        eventPublisher.publish("route.updated", event);
        return updated;
    }

    @Transactional
    public FlightRouteResponseDTO handle(DeactivateFlightRouteCommand command) {
        log.info("CQRS Command: Deactivating route {}", command.getRouteId());
        FlightRouteResponseDTO deactivated = flightRouteService.deactivateRoute(command.getRouteId());

        FlightRouteDeactivatedEvent event = new FlightRouteDeactivatedEvent(
                null,
                command.getRouteId(),
                command.getReason() != null ? command.getReason() : "Deactivated by operator"
        );

        eventPublisher.publish("route.deactivated", event);
        return deactivated;
    }
}
