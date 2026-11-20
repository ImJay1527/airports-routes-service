package pt.isep.sidis.airports.unicorns.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO representing the emotional and operational mood status of aircraft.
 * Part of the mandatory Sprint 2 CQRS query projection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Airplane mood and happiness status representation (CQRS Query Projection)")
public class UnicornMoodDTO {

    @Schema(description = "Airplane model name", example = "A320neo")
    private String airplaneModel;

    @Schema(description = "Aircraft tail registration number", example = "CS-TND")
    private String registration;

    @Schema(description = "Current emotional/vibe state of the airplane", example = "ECSTATIC")
    private String mood;

    @Schema(description = "Happiness and operational harmony score out of 10.0", example = "9.8")
    private Double vibeScore;

    @Schema(description = "Status description explaining the airplane's current mood",
            example = "Smooth landing on LIS runway 03/21, certified and refueled.")
    private String statusMessage;

    @Schema(description = "Last known airport location (IATA)", example = "LIS")
    private String lastKnownAirport;

    @Schema(description = "Whether rainbow engine sparkles are active", example = "true")
    private Boolean sparklesEnabled;

    @Schema(description = "CQRS architectural integration explanation",
            example = "Read-side projection derived asynchronously from flight/route domain events.")
    private String cqrsRole;

    @Schema(description = "Timestamp of the last mood evaluation")
    private Instant lastUpdated;
}
