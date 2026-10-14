package pt.isep.sidis.airports.peers;

import pt.isep.sidis.airports.internal.AirportInfo;
import pt.isep.sidis.airports.internal.RouteInfo;

import java.util.Optional;

/**
 * Contract for fetching airport and flight route data from peer replica instances.
 */
public interface PeerClient {

    Optional<AirportInfo> fetchAirportFromPeers(String iata, String requestId);

    Optional<RouteInfo> fetchRouteFromPeers(String routeId, String requestId);
}
