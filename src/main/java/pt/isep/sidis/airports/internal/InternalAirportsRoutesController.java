package pt.isep.sidis.airports.internal;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.sidis.airports.airportmanagement.domain.AirplaneCertification;
import pt.isep.sidis.airports.airportmanagement.domain.Airport;
import pt.isep.sidis.airports.airportmanagement.repositories.AirportRepository;
import pt.isep.sidis.airports.flightroutes.domain.FlightRoute;
import pt.isep.sidis.airports.flightroutes.repositories.FlightRouteRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service-to-service endpoints (role SERVICE only, enforced in SecurityConfig).
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "Internal (service-to-service)")
@Transactional(readOnly = true)
public class InternalAirportsRoutesController {

    private final AirportRepository airportRepository;
    private final FlightRouteRepository routeRepository;

    public InternalAirportsRoutesController(AirportRepository airportRepository, FlightRouteRepository routeRepository) {
        this.airportRepository = airportRepository;
        this.routeRepository = routeRepository;
    }

    @GetMapping("/routes/{routeId}")
    public ResponseEntity<RouteInfo> getRoute(@PathVariable String routeId) {
        Optional<FlightRoute> optRoute = routeRepository.findById(routeId);
        if (optRoute.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        FlightRoute route = optRoute.get();
        AirportInfo originInfo = toAirportInfo(route.getOrigin());
        AirportInfo destInfo = toAirportInfo(route.getDestination());

        RouteInfo info = new RouteInfo(
                route.getRouteIdValue(),
                route.getRouteStatus().name(),
                route.getDistance(),
                route.getRouteRequirement().getMinRangeRequired(),
                route.getRouteRequirement().getMinCapacityRequired(),
                originInfo,
                destInfo
        );
        return ResponseEntity.ok(info);
    }

    @GetMapping("/airports/{iata}")
    public ResponseEntity<AirportInfo> getAirport(@PathVariable String iata) {
        Optional<Airport> optAirport = airportRepository.findByIataCode_Code(iata.toUpperCase());
        return optAirport.map(airport -> ResponseEntity.ok(toAirportInfo(airport)))
                .orElseGet(() -> ResponseEntity.notFound().build());
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
