package pt.isep.sidis.airports.cqrs.queries;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.airportmanagement.api.dto.BusiestAirportDTO;
import pt.isep.sidis.airports.flightroutes.api.dto.AlternativeRouteResponseDTO;
import pt.isep.sidis.airports.flightroutes.api.dto.FlightRouteResponseDTO;
import pt.isep.sidis.airports.flightroutes.domain.RouteStatus;
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

    public List<FlightRouteResponseDTO.RouteHistoryDTO> getRouteHistory(String routeId) {
        return flightRouteService.getRouteHistory(routeId);
    }

    public Page<FlightRouteResponseDTO> searchRoutes(String originIata, String destinationIata, Pageable pageable) {
        return flightRouteService.searchRoutes(originIata, destinationIata, pageable);
    }

    public Page<FlightRouteResponseDTO> getActiveRoutesSorted(RouteStatus filterStatus, String sortBy, Pageable pageable) {
        return flightRouteService.getActiveRoutesSorted(filterStatus, sortBy, pageable);
    }

    public Double getTotalNetworkDistance() {
        return flightRouteService.getTotalNetworkDistance();
    }

    public List<AlternativeRouteResponseDTO> findAlternativeRoutes(String originIata, String destinationIata, String algorithm) {
        return flightRouteService.findAlternativeRoutes(originIata, destinationIata, algorithm);
    }

    public String exportGeoJson() {
        return flightRouteService.exportGeoJson();
    }

    public String exportKml() {
        return flightRouteService.exportKml();
    }

    public Page<FlightRouteResponseDTO> getRoutesByAirport(String iataCode, Pageable pageable) {
        return flightRouteService.getRoutesByAirport(iataCode, pageable);
    }

    public List<BusiestAirportDTO> getBusiestAirports() {
        return flightRouteService.getBusiestAirports();
    }
}
