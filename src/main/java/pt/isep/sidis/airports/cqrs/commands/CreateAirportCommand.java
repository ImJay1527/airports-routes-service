package pt.isep.sidis.airports.cqrs.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.isep.sidis.airports.airportmanagement.api.dto.CreateAirportRequestDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAirportCommand {
    private CreateAirportRequestDTO requestDTO;
}
