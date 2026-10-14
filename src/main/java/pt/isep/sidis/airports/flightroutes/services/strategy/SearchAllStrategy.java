package pt.isep.sidis.airports.flightroutes.services.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import pt.isep.sidis.airports.flightroutes.domain.FlightRoute;
import pt.isep.sidis.airports.flightroutes.repositories.FlightRouteRepository;

@Component
@Order(Integer.MAX_VALUE)
public class SearchAllStrategy implements RouteSearchStrategy {

    private final FlightRouteRepository repository;

    public SearchAllStrategy(FlightRouteRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean supports(String originIata, String destinationIata) {
        return true;
    }

    @Override
    public Page<FlightRoute> execute(String originIata, String destinationIata, Pageable pageable) {
        return repository.findAll(pageable);
    }
}