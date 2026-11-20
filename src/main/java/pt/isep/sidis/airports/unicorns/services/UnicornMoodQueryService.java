package pt.isep.sidis.airports.unicorns.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.isep.sidis.airports.unicorns.dto.UnicornMoodDTO;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CQRS Query Projection Service for Airplane Moods.
 * Demonstrates the CQRS pattern:
 * - Read model optimized purely for low-latency queries.
 * - Decoupled from transactional relational write tables.
 * - Asynchronously updated via domain events without locking write models.
 */
@Service
public class UnicornMoodQueryService {

    private static final Logger log = LoggerFactory.getLogger(UnicornMoodQueryService.class);

    private final Map<String, UnicornMoodDTO> moodProjection = new ConcurrentHashMap<>();

    public UnicornMoodQueryService() {
        initializeProjection();
    }

    private void initializeProjection() {
        moodProjection.put("A320neo", UnicornMoodDTO.builder()
                .airplaneModel("A320neo")
                .registration("CS-TND")
                .mood("ECSTATIC")
                .vibeScore(9.8)
                .statusMessage("Smooth approach at LIS runway 03/21 with certified models and zero delays.")
                .lastKnownAirport("LIS")
                .sparklesEnabled(true)
                .cqrsRole("CQRS Read Projection (Event-driven materialized view)")
                .lastUpdated(Instant.now())
                .build());

        moodProjection.put("737 MAX", UnicornMoodDTO.builder()
                .airplaneModel("737 MAX")
                .registration("CS-TNK")
                .mood("HAPPY")
                .vibeScore(9.2)
                .statusMessage("Cruising at flight level FL360 on route-opo-lis with optimal fuel efficiency.")
                .lastKnownAirport("OPO")
                .sparklesEnabled(true)
                .cqrsRole("CQRS Read Projection (Event-driven materialized view)")
                .lastUpdated(Instant.now())
                .build());

        moodProjection.put("A330-900neo", UnicornMoodDTO.builder()
                .airplaneModel("A330-900neo")
                .registration("CS-TUQ")
                .mood("ZEN")
                .vibeScore(8.9)
                .statusMessage("Parked comfortably at FAO gate, ready for oceanic route clearance.")
                .lastKnownAirport("FAO")
                .sparklesEnabled(false)
                .cqrsRole("CQRS Read Projection (Event-driven materialized view)")
                .lastUpdated(Instant.now())
                .build());

        moodProjection.put("Embraer 190", UnicornMoodDTO.builder()
                .airplaneModel("Embraer 190")
                .registration("CS-TPO")
                .mood("EXCITED")
                .vibeScore(9.5)
                .statusMessage("Regional hopping between Portuguese aerodromes, high turnaround speed.")
                .lastKnownAirport("LIS")
                .sparklesEnabled(true)
                .cqrsRole("CQRS Read Projection (Event-driven materialized view)")
                .lastUpdated(Instant.now())
                .build());
    }

    public List<UnicornMoodDTO> getAllMoods() {
        return new ArrayList<>(moodProjection.values());
    }

    public Optional<UnicornMoodDTO> getMoodByModel(String model) {
        if (model == null) return Optional.empty();
        return Optional.ofNullable(moodProjection.get(model));
    }

    /**
     * CQRS Event-Driven update hook: updates the read projection when a route or airport event occurs.
     */
    public void recordRouteActivity(String routeId, String origin, String destination) {
        log.info("Updating unicorn airplane mood projection based on route activity: {}", routeId);
        moodProjection.computeIfPresent("A320neo", (k, v) -> {
            v.setLastKnownAirport(destination);
            v.setMood("ECSTATIC");
            v.setVibeScore(10.0);
            v.setStatusMessage("Completed flight on " + routeId + " to " + destination + " with rainbow trailing.");
            v.setLastUpdated(Instant.now());
            return v;
        });
    }

    public void recordAirportStatus(String iata, String status) {
        log.info("Updating unicorn airplane mood projection based on airport status: {} -> {}", iata, status);
        if ("CLOSED".equalsIgnoreCase(status) || "UNDER_MAINTENANCE".equalsIgnoreCase(status)) {
            moodProjection.values().stream()
                    .filter(dto -> iata.equalsIgnoreCase(dto.getLastKnownAirport()))
                    .forEach(dto -> {
                        dto.setMood("MAINTENANCE_BLUES");
                        dto.setVibeScore(5.0);
                        dto.setStatusMessage("Grounded at " + iata + " due to airport maintenance status.");
                        dto.setSparklesEnabled(false);
                        dto.setLastUpdated(Instant.now());
                    });
        }
    }
}
