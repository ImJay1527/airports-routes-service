package pt.isep.sidis.airports.unicorns.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.isep.sidis.airports.unicorns.dto.UnicornMoodDTO;
import pt.isep.sidis.airports.unicorns.services.UnicornMoodQueryService;

import java.util.List;

/**
 * Assignment 2 Mandatory Mock Endpoint: /api/v2/unicorns.
 * Returns the mood status of airplanes in JSON format.
 *
 * CQRS Integration Explanation:
 * In accordance with the CQRS pattern (Command-Query Responsibility Segregation), this endpoint
 * acts as a pure Query Projection (Read Model). It does not issue transactional commands or lock
 * write-side entities. Instead, it serves pre-computed, materialized airplane mood states derived
 * asynchronously from domain events (route scheduling and airport operations).
 */
@RestController
@RequestMapping("/api/v2/unicorns")
@Tag(name = "Unicorns & Airplane Moods (CQRS)",
     description = "SIDIS Assignment 2 Mandatory CQRS Query Projection endpoint for airplane mood status")
public class UnicornMoodController {

    private final UnicornMoodQueryService unicornMoodQueryService;

    public UnicornMoodController(UnicornMoodQueryService unicornMoodQueryService) {
        this.unicornMoodQueryService = unicornMoodQueryService;
    }

    @GetMapping
    @Operation(summary = "Get Airplane Mood Statuses (CQRS Query)",
               description = "Returns current mood and vibe status of all airplanes as a CQRS query projection.")
    public ResponseEntity<List<UnicornMoodDTO>> getAirplaneMoods(
            @Parameter(description = "Optional filter by airplane model name (e.g., A320neo, 737 MAX)")
            @RequestParam(required = false) String model) {

        if (model != null && !model.isBlank()) {
            return unicornMoodQueryService.getMoodByModel(model)
                    .map(dto -> ResponseEntity.ok(List.of(dto)))
                    .orElse(ResponseEntity.notFound().build());
        }

        return ResponseEntity.ok(unicornMoodQueryService.getAllMoods());
    }
}
