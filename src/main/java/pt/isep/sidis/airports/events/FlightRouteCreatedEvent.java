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
    private Double minRangeRequired;
    private Integer minCapacityRequired;
    private Integer estimatedFlightTime;
    private String status;

    public FlightRouteCreatedEvent(String sourceInstance, String routeId, String originIata,
                                   String destinationIata, Double distanceKm,
                                   Double minRangeRequired, Integer minCapacityRequired,
                                   Integer estimatedFlightTime, String status) {
        super("FlightRouteCreatedEvent", sourceInstance);
        this.routeId = routeId;
        this.originIata = originIata;
        this.destinationIata = destinationIata;
        this.distanceKm = distanceKm;
        this.minRangeRequired = minRangeRequired;
        this.minCapacityRequired = minCapacityRequired;
        this.estimatedFlightTime = estimatedFlightTime;
        this.status = status;
    }
}
