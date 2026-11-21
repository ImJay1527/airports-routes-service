package pt.isep.sidis.airports.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AirportStatusUpdatedEvent extends DomainEvent {

    private String iataCode;
    private String newStatus;

    public AirportStatusUpdatedEvent(String sourceInstance, String iataCode, String newStatus) {
        super("AirportStatusChangedEvent", sourceInstance);
        this.iataCode = iataCode;
        this.newStatus = newStatus;
    }
}
