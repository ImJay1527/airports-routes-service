package pt.isep.sidis.airports.internal;

/**
 * Contract with flight-operations-service: response of GET /internal/routes/{routeId}.
 * Do NOT rename or remove fields without agreeing with the Flight Operations owner (see README).
 */
public record RouteInfo(
        String routeId,
        String status,              // ACTIVE | DEACTIVATED
        double distanceKm,
        double minRangeRequired,
        int minCapacityRequired,
        AirportInfo origin,
        AirportInfo destination) {
}
