package pt.isep.sidis.airports.cqrs.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.isep.sidis.airports.flightroutes.api.dto.UpdateFlightRouteDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFlightRouteCommand {
    private String routeId;
    private UpdateFlightRouteDTO dto;
}
