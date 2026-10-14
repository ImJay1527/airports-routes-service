package pt.isep.sidis.airports.airportmanagement.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateAirportDetailsRequestDTO {
    @Valid
    private OperationalHoursDTO operationalHours;

    @Valid
    private List<ContactDTO> contacts;

    @NotNull(message = "The version is mandatory for optimistic locking.")
    private Long version;
}
