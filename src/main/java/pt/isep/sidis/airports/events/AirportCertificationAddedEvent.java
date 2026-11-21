package pt.isep.sidis.airports.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AirportCertificationAddedEvent extends DomainEvent {

    private String iataCode;
    private List<String> certifiedModels;

    public AirportCertificationAddedEvent(String sourceInstance, String iataCode, List<String> certifiedModels) {
        super("AirportCertificationAddedEvent", sourceInstance);
        this.iataCode = iataCode;
        this.certifiedModels = certifiedModels;
    }
}
