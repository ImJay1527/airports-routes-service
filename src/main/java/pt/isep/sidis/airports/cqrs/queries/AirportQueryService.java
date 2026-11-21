package pt.isep.sidis.airports.cqrs.queries;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.airportmanagement.domain.Airport;
import pt.isep.sidis.airports.airportmanagement.services.AirportService;
import pt.isep.sidis.airports.common.exceptions.ResourceNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CQRS Query Service for Airports.
 * Dedicated read model for serving airport queries without write locks.
 */
@Service
@Transactional(readOnly = true)
public class AirportQueryService {

    private final AirportService airportService;

    public AirportQueryService(AirportService airportService) {
        this.airportService = airportService;
    }

    public List<Airport> getAllAirports() {
        return airportService.getAllAirports();
    }

    public List<Airport> searchAirports(String city, String country, String name) {
        if (city == null && country == null && name == null) {
            return airportService.getAllAirports();
        }
        return airportService.searchAirports(city, country, name);
    }

    public Airport getAirportByIata(String iataCode) {
        return airportService.getAirportDetails(iataCode);
    }

    public Optional<Airport> findByIata(String iataCode) {
        try {
            return Optional.ofNullable(airportService.getAirportDetails(iataCode));
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }

    public Map<String, List<Airport>> getAirportsGroupedBy(String groupBy) {
        return airportService.getAirportsGroupedBy(groupBy);
    }
}
