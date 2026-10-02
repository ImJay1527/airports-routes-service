package pt.isep.sidis.airports.internal;

import java.util.List;

/**
 * Contract with flight-operations-service: response of GET /internal/airports/{iata} (also embedded in RouteInfo).
 * Do NOT rename or remove fields without agreeing with the Flight Operations owner (see README).
 */
public record AirportInfo(
        String iataCode,
        String status,                  // OPERATIONAL | CLOSED | UNDER_MAINTENANCE
        List<String> certifiedModels) { // aircraft model names allowed to fly to/from this airport
}
