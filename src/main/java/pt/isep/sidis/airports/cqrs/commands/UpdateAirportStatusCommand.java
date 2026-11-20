package pt.isep.sidis.airports.cqrs.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAirportStatusCommand {
    private String iataCode;
    private String newStatus;
    private Long version;

    public UpdateAirportStatusCommand(String iataCode, String newStatus) {
        this.iataCode = iataCode;
        this.newStatus = newStatus;
    }
}
