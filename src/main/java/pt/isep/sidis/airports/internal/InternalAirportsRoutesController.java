package pt.isep.sidis.airports.internal;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pt.isep.sidis.airports.airportmanagement.domain.AirplaneCertification;
import pt.isep.sidis.airports.airportmanagement.domain.Airport;
import pt.isep.sidis.airports.airportmanagement.repositories.AirportRepository;
import pt.isep.sidis.airports.flightroutes.domain.FlightRoute;
import pt.isep.sidis.airports.flightroutes.repositories.FlightRouteRepository;
import pt.isep.sidis.airports.peers.PeerClient;

import java.util.List;
import java.util.Optional;

/**
 * Service-to-service endpoints (role SERVICE only, enforced in SecurityConfig).
 * Implements Unicast Peer Query Forwarding with loop prevention (X-Forwarded-From).
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "Internal (service-to-service)")
@Transactional(readOnly = true)
public class InternalAirportsRoutesController {

    private final AirportRepository airportRepository;
    private final FlightRouteRepository routeRepository;
    private final PeerClient peerClient;

    public InternalAirportsRoutesController(AirportRepository airportRepository,
                                            FlightRouteRepository routeRepository,
                                            PeerClient peerClient) {
        this.airportRepository = airportRepository;
        this.routeRepository = routeRepository;
        this.peerClient = peerClient;
    }

    @GetMapping("/routes/{routeId}")
    public ResponseEntity<RouteInfo> getRoute(
            @PathVariable String routeId,
            @RequestHeader(value = "X-Forwarded-From", required = false) String forwardedFrom,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletResponse response) {

        Optional<FlightRoute> optRoute = routeRepository.findById(routeId);
        if (optRoute.isPresent()) {
            if (response != null) {
                response.setHeader("X-Data-Source", "local");
            }
            return ResponseEntity.ok(toRouteInfo(optRoute.get()));
        }

        // Loop prevention: if already forwarded from a peer, answer from local data only
        if (forwardedFrom != null && !forwardedFrom.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        // Forward query to peer replicas using unicast HTTP GET
        Optional<RouteInfo> fromPeer = peerClient.fetchRouteFromPeers(routeId, requestId);
        if (fromPeer.isPresent()) {
            if (response != null) {
                response.setHeader("X-Data-Source", "peer");
            }
            return ResponseEntity.ok(fromPeer.get());
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/airports/{iata}")
    public ResponseEntity<AirportInfo> getAirport(
            @PathVariable String iata,
            @RequestHeader(value = "X-Forwarded-From", required = false) String forwardedFrom,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletResponse response) {

        Optional<Airport> optAirport = airportRepository.findByIataCode_Code(iata.toUpperCase());
        if (optAirport.isPresent()) {
            if (response != null) {
                response.setHeader("X-Data-Source", "local");
            }
            return ResponseEntity.ok(toAirportInfo(optAirport.get()));
        }

        // Loop prevention: if already forwarded from a peer, answer from local data only
        if (forwardedFrom != null && !forwardedFrom.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        // Forward query to peer replicas using unicast HTTP GET
        Optional<AirportInfo> fromPeer = peerClient.fetchAirportFromPeers(iata.toUpperCase(), requestId);
        if (fromPeer.isPresent()) {
            if (response != null) {
                response.setHeader("X-Data-Source", "peer");
            }
            return ResponseEntity.ok(fromPeer.get());
        }

        return ResponseEntity.notFound().build();
    }

    private RouteInfo toRouteInfo(FlightRoute route) {
        AirportInfo originInfo = toAirportInfo(route.getOrigin());
        AirportInfo destInfo = toAirportInfo(route.getDestination());
        return new RouteInfo(
                route.getRouteIdValue(),
                route.getRouteStatus().name(),
                route.getDistance(),
                route.getRouteRequirement().getMinRangeRequired(),
                route.getRouteRequirement().getMinCapacityRequired(),
                originInfo,
                destInfo
        );
    }

    private AirportInfo toAirportInfo(Airport airport) {
        List<String> models = airport.getCertifications() != null
                ? airport.getCertifications().stream().map(AirplaneCertification::getModelName).toList()
                : List.of();
        return new AirportInfo(
                airport.getIataCode().getCode(),
                airport.getStatus().name(),
                models
        );
    }
}
