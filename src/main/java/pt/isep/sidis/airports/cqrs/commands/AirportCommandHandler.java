package pt.isep.sidis.airports.cqrs.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.airportmanagement.domain.AirplaneCertification;
import pt.isep.sidis.airports.airportmanagement.domain.Airport;
import pt.isep.sidis.airports.airportmanagement.domain.Contact;
import pt.isep.sidis.airports.airportmanagement.services.AirportImportResult;
import pt.isep.sidis.airports.airportmanagement.services.AirportService;
import pt.isep.sidis.airports.events.AirportCertificationAddedEvent;
import pt.isep.sidis.airports.events.AirportCreatedEvent;
import pt.isep.sidis.airports.events.AirportStatusUpdatedEvent;
import pt.isep.sidis.airports.events.AirportUpdatedEvent;
import pt.isep.sidis.airports.messaging.EventPublisher;

import java.util.List;

/**
 * CQRS Command Handler for Airport writes and mutations.
 * Enforces business rules and publishes domain events via RabbitMQ AMQP broker / Transactional Outbox.
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
    public AirportImportResult handle(ImportAirportsCommand command) {
        log.info("CQRS Command: Bulk importing airports from CSV");
        AirportImportResult result = airportService.importAirportsFromCsv(command.getFile());

        for (Airport imported : result.getCreatedAirports()) {
            List<String> models = imported.getCertifications() != null ?
                    imported.getCertifications().stream().map(AirplaneCertification::getModelName).toList() : List.of();

            AirportCreatedEvent event = new AirportCreatedEvent(
                    null,
                    imported.getIataCode().getCode(),
                    imported.getName(),
                    imported.getLocation() != null ? imported.getLocation().getCity() : null,
                    imported.getLocation() != null ? imported.getLocation().getCountry() : null,
                    imported.getStatus() != null ? imported.getStatus().name() : "OPERATIONAL",
                    models
            );
            eventPublisher.publish("airport.created", event);
            log.info("Published airport.created event for imported airport {}", imported.getIataCode().getCode());
        }

        return result;
    }

    @Transactional
    public Airport handle(UpdateAirportStatusCommand command) {
        log.info("CQRS Command: Updating status of airport {} to {} with version {}",
                command.getIataCode(), command.getNewStatus(), command.getVersion());

        Airport updated = airportService.changeOperationalStatus(
                command.getIataCode(),
                command.getNewStatus(),
                command.getVersion()
        );

        AirportStatusUpdatedEvent event = new AirportStatusUpdatedEvent(
                null,
                updated.getIataCode().getCode(),
                updated.getStatus().name()
        );

        eventPublisher.publish("airport.status.changed", event);
        return updated;
    }

    @Transactional
    public Airport handle(AddAirportCertificationCommand command) {
        log.info("CQRS Command: Adding airplane certification '{}' to airport {}",
                command.getAircraftModelName(), command.getIataCode());

        Airport updated = airportService.addAirplaneCertification(
                command.getIataCode(),
                command.getAircraftModelName(),
                command.getVersion()
        );

        List<String> allCertified = updated.getCertifications().stream()
                .map(AirplaneCertification::getModelName)
                .toList();

        AirportCertificationAddedEvent event = new AirportCertificationAddedEvent(
                null,
                updated.getIataCode().getCode(),
                allCertified
        );

        eventPublisher.publish("airport.certification.added", event);
        return updated;
    }

    @Transactional
    public Airport handle(UpdateAirportDetailsCommand command) {
        log.info("CQRS Command: Updating details of airport {}", command.getIataCode());
        Airport updated = airportService.updateAirportDetails(command.getIataCode(), command.getDto());

        List<String> models = updated.getCertifications() != null ?
                updated.getCertifications().stream().map(AirplaneCertification::getModelName).toList() : List.of();

        String opening = (updated.getOperationalHours() != null && updated.getOperationalHours().getOpeningTime() != null)
                ? updated.getOperationalHours().getOpeningTime().toString() : null;
        String closing = (updated.getOperationalHours() != null && updated.getOperationalHours().getClosingTime() != null)
                ? updated.getOperationalHours().getClosingTime().toString() : null;

        List<AirportUpdatedEvent.ContactEntry> contacts = (updated.getContacts() != null)
                ? updated.getContacts().stream()
                    .map(c -> new AirportUpdatedEvent.ContactEntry(c.getValue(), c.getDepartment(), c.getType() != null ? c.getType().name() : null))
                    .toList()
                : List.of();

        AirportUpdatedEvent event = new AirportUpdatedEvent(
                null,
                updated.getIataCode().getCode(),
                updated.getName(),
                updated.getLocation() != null ? updated.getLocation().getCity() : null,
                updated.getLocation() != null ? updated.getLocation().getCountry() : null,
                updated.getStatus() != null ? updated.getStatus().name() : "OPERATIONAL",
                models,
                opening,
                closing,
                contacts
        );

        eventPublisher.publish("airport.updated", event);
        return updated;
    }
}
