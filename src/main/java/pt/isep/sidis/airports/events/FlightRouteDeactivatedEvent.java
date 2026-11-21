package pt.isep.sidis.airports.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FlightRouteDeactivatedEvent extends DomainEvent {

    private String routeId;
    private String reason;

    public FlightRouteDeactivatedEvent(String sourceInstance, String routeId, String reason) {
        super("FlightRouteDeactivatedEvent", sourceInstance);
        this.routeId = routeId;
        this.reason = reason;
    }
}
