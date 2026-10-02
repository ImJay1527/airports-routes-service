package pt.isep.sidis.airports.internal;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints (role SERVICE only, enforced in SecurityConfig).
 *
 * TODO(owner): implement with the real repositories once airportmanagement / flightroutes are ported.
 *  - 200 + body if it exists (on this replica OR on a peer replica - ask peers before answering 404)
 *  - 404 if no replica has it
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "Internal (service-to-service)")
public class InternalAirportsRoutesController {

    @GetMapping("/routes/{routeId}")
    public ResponseEntity<RouteInfo> getRoute(@PathVariable String routeId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/airports/{iata}")
    public ResponseEntity<AirportInfo> getAirport(@PathVariable String iata) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
