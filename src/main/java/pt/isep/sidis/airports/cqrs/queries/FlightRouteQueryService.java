package pt.isep.sidis.airports.cqrs.queries;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.flightroutes.api.dto.FlightRouteResponseDTO;
import pt.isep.sidis.airports.flightroutes.services.FlightRouteService;

import java.util.List;

/**
 * CQRS Query Service for Flight Routes.
 * Dedicated read model for serving flight route queries without write locks.
 */
@Service
@Transactional(readOnly = true)
public class FlightRouteQueryService {

    private final FlightRouteService flightRouteService;

    public FlightRouteQueryService(FlightRouteService flightRouteService) {
        this.flightRouteService = flightRouteService;
    }

    public FlightRouteResponseDTO getRouteById(String routeId) {
        return flightRouteService.getRouteById(routeId);
    }

    public Double calculateTotalNetworkDistance() {
        return flightRouteService.getTotalNetworkDistance();
    }
}
