package pt.isep.sidis.airports.cqrs.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.airportmanagement.domain.AirplaneCertification;
import pt.isep.sidis.airports.airportmanagement.domain.Airport;
import pt.isep.sidis.airports.airportmanagement.services.AirportService;
import pt.isep.sidis.airports.events.AirportCreatedEvent;
import pt.isep.sidis.airports.events.AirportStatusUpdatedEvent;
import pt.isep.sidis.airports.messaging.EventPublisher;

import java.util.List;

/**
 * CQRS Command Handler for Airport writes and mutations.
 * Enforces business rules and publishes domain events via RabbitMQ AMQP broker.
 */
@Service
public class AirportCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AirportCommandHandler.class);

    private final AirportService airportService;
    private final EventPublisher eventPublisher;

    public AirportCommandHandler(AirportService airportService, EventPublisher eventPublisher) {
        this.airportService = airportService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Airport handle(CreateAirportCommand command) {
        log.info("CQRS Command: Creating airport {}", command.getRequestDTO().getIataCode());
        Airport created = airportService.createAirport(command.getRequestDTO());

        List<String> models = created.getCertifications() != null ?
                created.getCertifications().stream().map(AirplaneCertification::getModelName).toList() : List.of();

        AirportCreatedEvent event = new AirportCreatedEvent(
                null,
                created.getIataCode().getCode(),
                created.getName(),
                created.getLocation() != null ? created.getLocation().getCity() : null,
                created.getLocation() != null ? created.getLocation().getCountry() : null,
                created.getStatus() != null ? created.getStatus().name() : "OPERATIONAL",
                models
        );

        eventPublisher.publish("airport.created", event);
        return created;
    }

    @Transactional
    public Airport handle(UpdateAirportStatusCommand command) {
        log.info("CQRS Command: Updating status of airport {} to {}", command.getIataCode(), command.getNewStatus());
        Airport existing = airportService.getAirportDetails(command.getIataCode());
        Long version = command.getVersion() != null ? command.getVersion() : existing.getVersion();
        Airport updated = airportService.changeOperationalStatus(command.getIataCode(), command.getNewStatus(), version);

        AirportStatusUpdatedEvent event = new AirportStatusUpdatedEvent(
                null,
                updated.getIataCode().getCode(),
                updated.getStatus().name()
        );

        eventPublisher.publish("airport.status", event);
        return updated;
    }
}
