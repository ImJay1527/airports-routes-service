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
import pt.isep.sidis.airports.flightroutes.domain.RouteRequirement;
import pt.isep.sidis.airports.flightroutes.repositories.FlightRouteRepository;
import pt.isep.sidis.airports.unicorns.services.UnicornMoodQueryService;

import java.util.Optional;

/**
 * Asynchronous Event Listener for Cross-Instance Replication.
 * Fulfills Assignment 2 Event-Driven Architecture (AMQP / RabbitMQ):
 * - Replicas listen to domain events and update their local read-side databases asynchronously.
 * - Achieves Eventual Consistency across sharded / multi-instance nodes without synchronous blocking.
 * - Updates the Unicorn Mood CQRS Query Projection.
 */
@Component
public class ReplicaEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReplicaEventListener.class);

    private final AirportRepository airportRepository;
    private final FlightRouteRepository flightRouteRepository;
    private final ClusterConfig clusterConfig;
    private final UnicornMoodQueryService unicornMoodQueryService;

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

        log.info("Consuming replicated domain event '{}' from peer instance '{}'",
                event.getEventType(), event.getSourceInstance());

        if (event instanceof AirportCreatedEvent ace) {
            handleAirportCreated(ace);
        } else if (event instanceof AirportStatusUpdatedEvent asue) {
            handleAirportStatusUpdated(asue);
        } else if (event instanceof FlightRouteCreatedEvent frce) {
            handleFlightRouteCreated(frce);
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

        try {
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
        } catch (Exception e) {
            log.warn("Failed to replicate airport {}: {}", ace.getIataCode(), e.getMessage());
        }
    }

    private void handleAirportStatusUpdated(AirportStatusUpdatedEvent asue) {
        Optional<Airport> existing = airportRepository.findByIataCode_Code(asue.getIataCode());
        existing.ifPresent(airport -> {
            try {
                airport.changeStatus(Status.valueOf(asue.getNewStatus()));
                airportRepository.save(airport);
                log.info("Asynchronously updated status of airport {} to {} via RabbitMQ.",
                        asue.getIataCode(), asue.getNewStatus());
            } catch (Exception e) {
                log.warn("Could not update status of {}: {}", asue.getIataCode(), e.getMessage());
            }
        });
        unicornMoodQueryService.recordAirportStatus(asue.getIataCode(), asue.getNewStatus());
    }

    private void handleFlightRouteCreated(FlightRouteCreatedEvent frce) {
        if (flightRouteRepository.existsById(new pt.isep.sidis.airports.flightroutes.domain.RouteId(frce.getRouteId()))) {
            log.debug("Route {} already exists locally, skipping replication.", frce.getRouteId());
            return;
        }

        Optional<Airport> originOpt = airportRepository.findByIataCode_Code(frce.getOriginIata());
        Optional<Airport> destOpt = airportRepository.findByIataCode_Code(frce.getDestinationIata());

        if (originOpt.isPresent() && destOpt.isPresent()) {
            try {
                RouteRequirement req = new RouteRequirement(frce.getDistanceKm() != null ? frce.getDistanceKm() * 1.2 : 500.0, 100);
                FlightRoute route = new FlightRoute(frce.getRouteId(), originOpt.get(), destOpt.get(),
                        frce.getDistanceKm(), frce.getEstimatedFlightTime(), req, "system-replication");
                flightRouteRepository.save(route);
                log.info("Asynchronously replicated route {} into local database via RabbitMQ.", frce.getRouteId());
            } catch (Exception e) {
                log.warn("Failed to replicate route {}: {}", frce.getRouteId(), e.getMessage());
            }
        }
        unicornMoodQueryService.recordRouteActivity(frce.getRouteId(), frce.getOriginIata(), frce.getDestinationIata());
    }

    private void handleFlightRouteDeactivated(FlightRouteDeactivatedEvent frde) {
        flightRouteRepository.findById(new pt.isep.sidis.airports.flightroutes.domain.RouteId(frde.getRouteId())).ifPresent(route -> {
            try {
                route.deactivate("AMQP Event: " + frde.getReason());
                flightRouteRepository.save(route);
                log.info("Asynchronously deactivated route {} via RabbitMQ.", frde.getRouteId());
            } catch (Exception e) {
                log.warn("Failed to deactivate route {}: {}", frde.getRouteId(), e.getMessage());
            }
        });
    }
}
