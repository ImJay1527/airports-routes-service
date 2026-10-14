package pt.isep.sidis.airports.flightroutes.services.routing;

import pt.isep.sidis.airports.flightroutes.domain.FlightRoute;
import java.util.List;

public interface AlternativeRoutingStrategy {
    
    List<List<FlightRoute>> findAlternatives(String originIata, String destinationIata);
    
    String getAlgorithmName();
}