package pt.isep.sidis.airports.cqrs.queries;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.airportmanagement.domain.Airport;
import pt.isep.sidis.airports.airportmanagement.repositories.AirportRepository;
import pt.isep.sidis.airports.common.exceptions.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * CQRS Query Service for Airports.
 * Dedicated read model optimized for low-latency queries, independent of transactional command handlers.
 */
@Service
@Transactional(readOnly = true)
public class AirportQueryService {

    private final AirportRepository airportRepository;

    public AirportQueryService(AirportRepository airportRepository) {
        this.airportRepository = airportRepository;
    }

    public List<Airport> getAllAirports() {
        return airportRepository.findAll();
    }

    public Airport getAirportByIata(String iataCode) {
        return airportRepository.findByIataCode_Code(iataCode)
                .orElseThrow(() -> new ResourceNotFoundException("Airport with IATA code '" + iataCode + "' not found."));
    }

    public Optional<Airport> findByIata(String iataCode) {
        return airportRepository.findByIataCode_Code(iataCode);
    }
}
