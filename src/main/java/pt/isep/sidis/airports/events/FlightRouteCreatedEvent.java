package pt.isep.sidis.airports.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FlightRouteCreatedEvent extends DomainEvent {

    private String routeId;
    private String originIata;
    private String destinationIata;
    private Double distanceKm;
    private Integer estimatedFlightTime;
    private String status;

    public FlightRouteCreatedEvent(String sourceInstance, String routeId, String originIata,
                                   String destinationIata, Double distanceKm,
                                   Integer estimatedFlightTime, String status) {
        super("FLIGHT_ROUTE_CREATED", sourceInstance);
        this.routeId = routeId;
        this.originIata = originIata;
        this.destinationIata = destinationIata;
        this.distanceKm = distanceKm;
        this.estimatedFlightTime = estimatedFlightTime;
        this.status = status;
    }
}
