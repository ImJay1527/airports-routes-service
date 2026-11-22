package pt.isep.sidis.airports.cqrs.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.airportmanagement.domain.*;
import pt.isep.sidis.airports.airportmanagement.repositories.AirportRepository;
import pt.isep.sidis.airports.cluster.ClusterConfig;
import pt.isep.sidis.airports.events.*;
import pt.isep.sidis.airports.flightroutes.domain.FlightRoute;
import pt.isep.sidis.airports.flightroutes.domain.RouteId;
import pt.isep.sidis.airports.flightroutes.domain.RouteRequirement;
import pt.isep.sidis.airports.flightroutes.repositories.FlightRouteRepository;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Asynchronous Event Listener for Cross-Instance Replication.
 * Fulfills Assignment 2 Event-Driven Architecture (AMQP / RabbitMQ):
 * - Consumes raw AMQP messages with routing keys and deserializes to typed domain events.
 * - Replicas listen to domain events and update their local read-side databases asynchronously.
 * - Achieves Eventual Consistency across sharded / multi-instance nodes without synchronous blocking.
 * - Enforces retry policy and dead-lettering (DLX/DLQ) by letting processing exceptions propagate.
 * - Guarantees idempotency and protects against out-of-order event application via database timestamp checks.
 */
@Component
public class ReplicaEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReplicaEventListener.class);

    private final AirportRepository airportRepository;
    private final FlightRouteRepository flightRouteRepository;
    private final ClusterConfig clusterConfig;
    private final ObjectMapper objectMapper;

    public ReplicaEventListener(AirportRepository airportRepository,
                                FlightRouteRepository flightRouteRepository,
                                ClusterConfig clusterConfig,
                                ObjectMapper objectMapper) {
        this.airportRepository = airportRepository;
        this.flightRouteRepository = flightRouteRepository;
        this.clusterConfig = clusterConfig;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{@replicaQueue.name}")
    @Transactional
    public void handleMessage(Message message,
                              @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        if (message == null || message.getBody() == null) {
            return;
        }

        byte[] body = message.getBody();
        String rk = (routingKey != null) ? routingKey : "";

        try {
            JsonNode root = objectMapper.readTree(body);
            String source = root.has("sourceInstance") && !root.get("sourceInstance").isNull()
                    ? root.get("sourceInstance").asText() : null;

            // Prevent echo: ignore events produced by this replica itself
            if (clusterConfig.getSelfName() != null && clusterConfig.getSelfName().equals(source)) {
                log.debug("Ignoring event with routing key '{}' published by self ({})", rk, source);
                return;
            }

            log.info("Consuming replicated event with routing key '{}' from instance '{}'", rk, source);

            switch (rk) {
                case "airport.created" -> {
                    AirportCreatedEvent ace = objectMapper.readValue(body, AirportCreatedEvent.class);
                    handleAirportCreated(ace);
                }
                case "airport.status.changed", "airport.status" -> {
                    AirportStatusUpdatedEvent asue = objectMapper.readValue(body, AirportStatusUpdatedEvent.class);
                    handleAirportStatusUpdated(asue);
                }
                case "airport.certification.added" -> {
                    AirportCertificationAddedEvent acae = objectMapper.readValue(body, AirportCertificationAddedEvent.class);
                    handleAirportCertificationAdded(acae);
                }
                case "airport.updated" -> {
                    AirportUpdatedEvent aue = objectMapper.readValue(body, AirportUpdatedEvent.class);
                    handleAirportUpdated(aue);
                }
                case "route.created" -> {
                    FlightRouteCreatedEvent frce = objectMapper.readValue(body, FlightRouteCreatedEvent.class);
                    handleFlightRouteCreated(frce);
                }
                case "route.updated" -> {
                    FlightRouteUpdatedEvent frue = objectMapper.readValue(body, FlightRouteUpdatedEvent.class);
                    handleFlightRouteUpdated(frue);
                }
                case "route.deactivated" -> {
                    FlightRouteDeactivatedEvent frde = objectMapper.readValue(body, FlightRouteDeactivatedEvent.class);
                    handleFlightRouteDeactivated(frde);
                }
                case "aircraft.created", "aircraft.status.changed", "aircraft.status" -> {
                    log.debug("Received aircraft domain event with routing key '{}' - acknowledged.", rk);
                }
                default -> {
                    log.debug("Ignoring unhandled or peer routing key '{}'", rk);
                }
            }
        } catch (JsonProcessingException e) {
            // Point 3: Malformed JSON will never succeed; reject directly to DLQ without retries
            log.error("Malformed JSON payload for routing key '{}': {}. Rejecting immediately to DLQ without retry.",
                    rk, e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Malformed JSON payload for " + rk, e);
        } catch (IllegalStateException e) {
            // Propagate to trigger Spring AMQP retry policy (max 3 attempts) then DLQ
            log.warn("Transient replication prerequisite missing for routing key '{}': {}. Triggering retry.",
                    rk, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to process AMQP message with routing key '{}': {}", rk, e.getMessage(), e);
            throw new RuntimeException("Error processing message for " + rk, e);
        }
    }

    private void handleAirportCreated(AirportCreatedEvent ace) {
        Optional<Airport> existing = airportRepository.findByIataCode_Code(ace.getIataCode());
        if (existing.isPresent()) {
            log.debug("Airport {} already exists locally, skipping creation replication.", ace.getIataCode());
            return;
        }

        Location loc = new Location("Europe", ace.getCountry() != null ? ace.getCountry() : "Portugal",
                ace.getCity() != null ? ace.getCity() : ace.getName(), null);
        Airport airport = new Airport(new IATACode(ace.getIataCode()), ace.getName(), loc, new Timezone("UTC+00:00"));
        if (ace.getCertifiedModels() != null) {
            for (String model : ace.getCertifiedModels()) {
                airport.addCertification(model);
            }
        }
        airportRepository.save(airport);
        log.info("Asynchronously replicated airport {} into local database via RabbitMQ.", ace.getIataCode());
    }

    private void handleAirportStatusUpdated(AirportStatusUpdatedEvent asue) {
        Instant eventTime = asue.getOccurredAt() != null ? asue.getOccurredAt() : Instant.now();

        Airport airport = airportRepository.findByIataCode_Code(asue.getIataCode())
                .orElseThrow(() -> new IllegalStateException("Airport " + asue.getIataCode()
                        + " not found locally; triggering AMQP retry / DLQ"));

        // Point 5: Compare against persisted timestamp in DB (survives restarts)
        if (airport.getLastStatusChangeAt() != null && eventTime.isBefore(airport.getLastStatusChangeAt())) {
            log.warn("Discarding out-of-order status update for airport {} (event time {} < last persisted {})",
                    asue.getIataCode(), eventTime, airport.getLastStatusChangeAt());
            return;
        }

        Status targetStatus = Status.valueOf(asue.getNewStatus());
        if (airport.getStatus() != targetStatus) {
            airport.changeStatus(targetStatus, eventTime);
            airportRepository.save(airport);
            log.info("Asynchronously updated status of airport {} to {} (timestamp: {}) via RabbitMQ.",
                    asue.getIataCode(), asue.getNewStatus(), eventTime);
        }
    }

    private void handleAirportCertificationAdded(AirportCertificationAddedEvent acae) {
        Airport airport = airportRepository.findByIataCode_Code(acae.getIataCode())
                .orElseThrow(() -> new IllegalStateException("Airport " + acae.getIataCode()
                        + " not found locally for certification update; triggering AMQP retry / DLQ"));

        if (acae.getCertifiedModels() != null) {
            for (String model : acae.getCertifiedModels()) {
                airport.addCertification(model);
            }
            airportRepository.save(airport);
            log.info("Asynchronously updated certifications for airport {} via RabbitMQ: {}",
                    acae.getIataCode(), acae.getCertifiedModels());
        }
    }

    private void handleAirportUpdated(AirportUpdatedEvent aue) {
        Airport airport = airportRepository.findByIataCode_Code(aue.getIataCode())
                .orElseThrow(() -> new IllegalStateException("Airport " + aue.getIataCode()
                        + " not found locally for detail update; triggering AMQP retry / DLQ"));

        if (aue.getOpeningTime() != null && aue.getClosingTime() != null) {
            OperationalHours hours = new OperationalHours(
                    LocalTime.parse(aue.getOpeningTime()),
                    LocalTime.parse(aue.getClosingTime())
            );
            List<Contact> contacts = new ArrayList<>();
            if (aue.getContacts() != null) {
                for (AirportUpdatedEvent.ContactEntry ce : aue.getContacts()) {
                    ContactType type = (ce.getType() != null) ? ContactType.valueOf(ce.getType()) : ContactType.PHONE;
                    contacts.add(new Contact(ce.getValue(), ce.getDepartment(), type));
                }
            }
            airport.updateDetails(hours, contacts);
        }

        if (aue.getCertifiedModels() != null) {
            for (String model : aue.getCertifiedModels()) {
                airport.addCertification(model);
            }
        }

        airportRepository.save(airport);
        log.info("Asynchronously updated details of airport {} via RabbitMQ.", aue.getIataCode());
    }

    private void handleFlightRouteCreated(FlightRouteCreatedEvent frce) {
        if (flightRouteRepository.existsById(new RouteId(frce.getRouteId()))) {
            log.debug("Route {} already exists locally, skipping replication.", frce.getRouteId());
            return;
        }

        // If origin or destination airports have not arrived yet, let exception bubble up
        // so Spring AMQP simple retry triggers up to 3 times before routing to DLQ.
        Airport origin = airportRepository.findByIataCode_Code(frce.getOriginIata())
                .orElseThrow(() -> new IllegalStateException("Origin airport " + frce.getOriginIata()
                        + " not found locally for route " + frce.getRouteId() + "; triggering AMQP retry"));

        Airport dest = airportRepository.findByIataCode_Code(frce.getDestinationIata())
                .orElseThrow(() -> new IllegalStateException("Destination airport " + frce.getDestinationIata()
                        + " not found locally for route " + frce.getRouteId() + "; triggering AMQP retry"));

        Double minRange = frce.getMinRangeRequired() != null ? frce.getMinRangeRequired()
                : (frce.getDistanceKm() != null ? frce.getDistanceKm() * 1.1 : 500.0);
        Integer minCapacity = frce.getMinCapacityRequired() != null ? frce.getMinCapacityRequired() : 100;

        RouteRequirement req = new RouteRequirement(minRange, minCapacity);
        FlightRoute route = new FlightRoute(frce.getRouteId(), origin, dest,
                frce.getDistanceKm(), frce.getEstimatedFlightTime(), req, "system-amqp-replication");

        flightRouteRepository.save(route);
        log.info("Asynchronously replicated route {} into local database via RabbitMQ.", frce.getRouteId());
    }

    private void handleFlightRouteUpdated(FlightRouteUpdatedEvent frue) {
        FlightRoute route = flightRouteRepository.findById(new RouteId(frue.getRouteId()))
                .orElseThrow(() -> new IllegalStateException("Route " + frue.getRouteId()
                        + " not found locally for update; triggering AMQP retry"));

        Double minRange = frue.getMinRangeRequired() != null ? frue.getMinRangeRequired() : 500.0;
        Integer minCapacity = frue.getMinCapacityRequired() != null ? frue.getMinCapacityRequired() : 100;
        Double distance = frue.getDistanceKm() != null ? frue.getDistanceKm() : route.getDistance();
        Integer time = frue.getEstimatedFlightTime() != null ? frue.getEstimatedFlightTime() : route.getEstimatedFlightTime();

        route.updateDetails(distance, time, new RouteRequirement(minRange, minCapacity), "AMQP Update");
        flightRouteRepository.save(route);
        log.info("Asynchronously updated route {} via RabbitMQ.", frue.getRouteId());
    }

    private void handleFlightRouteDeactivated(FlightRouteDeactivatedEvent frde) {
        flightRouteRepository.findById(new RouteId(frde.getRouteId())).ifPresent(route -> {
            route.deactivate("AMQP Event: " + frde.getReason());
            flightRouteRepository.save(route);
            log.info("Asynchronously deactivated route {} via RabbitMQ.", frde.getRouteId());
        });
    }
}
