package pt.isep.sidis.airports.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AirportUpdatedEvent extends DomainEvent {

    private String iataCode;
    private String name;
    private String city;
    private String country;
    private String status;
    private List<String> certifiedModels;
    private String openingTime;
    private String closingTime;
    private List<ContactEntry> contacts;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ContactEntry {
        private String value;
        private String department;
        private String type;

        public ContactEntry(String value, String department, String type) {
            this.value = value;
            this.department = department;
            this.type = type;
        }
    }

    public AirportUpdatedEvent(String sourceInstance, String iataCode, String name,
                               String city, String country, String status,
                               List<String> certifiedModels, String openingTime,
                               String closingTime, List<ContactEntry> contacts) {
        super("AirportUpdatedEvent", sourceInstance);
        this.iataCode = iataCode;
        this.name = name;
        this.city = city;
        this.country = country;
        this.status = status;
        this.certifiedModels = certifiedModels;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.contacts = contacts;
    }
}
