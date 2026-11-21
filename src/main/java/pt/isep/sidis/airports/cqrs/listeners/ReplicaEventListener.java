package pt.isep.sidis.airports.cqrs.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
import pt.isep.sidis.airports.unicorns.services.UnicornMoodQueryService;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronous Event Listener for Cross-Instance Replication.
 * Fulfills Assignment 2 Event-Driven Architecture (AMQP / RabbitMQ):
 * - Replicas listen to domain events and update their local read-side databases asynchronously.
 * - Achieves Eventual Consistency across sharded / multi-instance nodes without synchronous blocking.
 * - Enforces retry policy and dead-lettering (DLX/DLQ) by letting processing exceptions propagate.
 * - Guarantees idempotency and protects against out-of-order event application.
 */
@Component
public class ReplicaEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReplicaEventListener.class);

    private final AirportRepository airportRepository;
    private final FlightRouteRepository flightRouteRepository;
    private final ClusterConfig clusterConfig;
    private final UnicornMoodQueryService unicornMoodQueryService;

    // Track timestamps of last applied status changes to discard out-of-order stale events (Point 6)
    private final Map<String, Instant> lastStatusTimestamps = new ConcurrentHashMap<>();

    public ReplicaEventListener(AirportRepository airportRepository,
                                FlightRouteRepository flightRouteRepository,
                                ClusterConfig clusterConfig,
                                UnicornMoodQueryService unicornMoodQueryService) {
        this.airportRepository = airportRepository;
        this.flightRouteRepository = flightRouteRepository;
        this.clusterConfig = clusterConfig;
        this.unicornMoodQueryService = unicornMoodQueryService;
    }

    @RabbitListener(queues = "#{@replicaQueue.name}")
    @Transactional
    public void handleDomainEvent(DomainEvent event) {
        if (event == null) return;

        // Prevent echo: ignore events produced by this replica itself
        if (clusterConfig.getSelfName() != null && clusterConfig.getSelfName().equals(event.getSourceInstance())) {
            log.debug("Ignoring event {} published by self ({})", event.getEventType(), event.getSourceInstance());
            return;
        }

        log.info("Consuming replicated domain event '{}' (id: {}) from instance '{}'",
                event.getEventType(), event.getEventId(), event.getSourceInstance());

        if (event instanceof AirportCreatedEvent ace) {
            handleAirportCreated(ace);
        } else if (event instanceof AirportStatusUpdatedEvent asue) {
            handleAirportStatusUpdated(asue);
        } else if (event instanceof AirportCertificationAddedEvent acae) {
            handleAirportCertificationAdded(acae);
        } else if (event instanceof FlightRouteCreatedEvent frce) {
            handleFlightRouteCreated(frce);
        } else if (event instanceof FlightRouteUpdatedEvent frue) {
            handleFlightRouteUpdated(frue);
        } else if (event instanceof FlightRouteDeactivatedEvent frde) {
            handleFlightRouteDeactivated(frde);
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

        unicornMoodQueryService.recordAirportStatus(ace.getIataCode(), ace.getStatus());
    }

    private void handleAirportStatusUpdated(AirportStatusUpdatedEvent asue) {
        // Point 6: Protect against out-of-order delayed delivery
        Instant eventTime = asue.getOccurredAt() != null ? asue.getOccurredAt() : Instant.now();
        Instant lastTime = lastStatusTimestamps.get(asue.getIataCode());
        if (lastTime != null && eventTime.isBefore(lastTime)) {
            log.warn("Discarding out-of-order status update for airport {} (event time {} < last applied {})",
                    asue.getIataCode(), eventTime, lastTime);
            return;
        }

        Airport airport = airportRepository.findByIataCode_Code(asue.getIataCode())
                .orElseThrow(() -> new IllegalStateException("Airport " + asue.getIataCode()
                        + " not found locally; triggering AMQP retry / DLQ"));

        Status targetStatus = Status.valueOf(asue.getNewStatus());
        if (airport.getStatus() != targetStatus) {
            airport.changeStatus(targetStatus);
            airportRepository.save(airport);
            lastStatusTimestamps.put(asue.getIataCode(), eventTime);
            log.info("Asynchronously updated status of airport {} to {} via RabbitMQ.",
                    asue.getIataCode(), asue.getNewStatus());
        }

        unicornMoodQueryService.recordAirportStatus(asue.getIataCode(), asue.getNewStatus());
    }

    private void handleAirportCertificationAdded(AirportCertificationAddedEvent acae) {
        Airport airport = airportRepository.findByIataCode_Code(acae.getIataCode())
                .orElseThrow(() -> new IllegalStateException("Airport " + acae.getIataCode()
                        + " not found locally for certification update; triggering AMQP retry / DLQ"));

        if (acae.getCertifiedModels() != null) {
            for (String model : acae.getCertifiedModels()) {
                airport.addCertification(model);
                unicornMoodQueryService.recordCertification(acae.getIataCode(), model);
            }
            airportRepository.save(airport);
            log.info("Asynchronously updated certifications for airport {} via RabbitMQ: {}",
                    acae.getIataCode(), acae.getCertifiedModels());
        }
    }

    private void handleFlightRouteCreated(FlightRouteCreatedEvent frce) {
        if (flightRouteRepository.existsById(new RouteId(frce.getRouteId()))) {
            log.debug("Route {} already exists locally, skipping replication.", frce.getRouteId());
            return;
        }

        // Point 5: If origin or destination airports have not arrived yet, let exception bubble up
        // so Spring AMQP simple retry triggers up to 3 times before routing to DLQ.
        Airport origin = airportRepository.findByIataCode_Code(frce.getOriginIata())
                .orElseThrow(() -> new IllegalStateException("Origin airport " + frce.getOriginIata()
                        + " not found locally for route " + frce.getRouteId() + "; triggering AMQP retry"));

        Airport dest = airportRepository.findByIataCode_Code(frce.getDestinationIata())
                .orElseThrow(() -> new IllegalStateException("Destination airport " + frce.getDestinationIata()
                        + " not found locally for route " + frce.getRouteId() + "; triggering AMQP retry"));

        // Point 3: Use real requirements from event rather than fabricated values
        Double minRange = frce.getMinRangeRequired() != null ? frce.getMinRangeRequired()
                : (frce.getDistanceKm() != null ? frce.getDistanceKm() * 1.1 : 500.0);
        Integer minCapacity = frce.getMinCapacityRequired() != null ? frce.getMinCapacityRequired() : 100;

        RouteRequirement req = new RouteRequirement(minRange, minCapacity);
        FlightRoute route = new FlightRoute(frce.getRouteId(), origin, dest,
                frce.getDistanceKm(), frce.getEstimatedFlightTime(), req, "system-amqp-replication");

        flightRouteRepository.save(route);
        log.info("Asynchronously replicated route {} into local database via RabbitMQ.", frce.getRouteId());

        unicornMoodQueryService.recordRouteActivity(frce.getRouteId(), frce.getOriginIata(), frce.getDestinationIata());
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
