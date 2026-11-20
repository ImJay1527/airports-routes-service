package pt.isep.sidis.airports.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AirportCreatedEvent extends DomainEvent {

    private String iataCode;
    private String name;
    private String city;
    private String country;
    private String status;
    private List<String> certifiedModels;

    public AirportCreatedEvent(String sourceInstance, String iataCode, String name,
                               String city, String country, String status,
                               List<String> certifiedModels) {
        super("AIRPORT_CREATED", sourceInstance);
        this.iataCode = iataCode;
        this.name = name;
        this.city = city;
        this.country = country;
        this.status = status;
        this.certifiedModels = certifiedModels;
    }
}
