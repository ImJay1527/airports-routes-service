package pt.isep.sidis.airports.unicorns.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.airportmanagement.domain.AirplaneCertification;
import pt.isep.sidis.airports.airportmanagement.domain.Airport;
import pt.isep.sidis.airports.airportmanagement.domain.Status;
import pt.isep.sidis.airports.airportmanagement.repositories.AirportRepository;
import pt.isep.sidis.airports.flightroutes.domain.FlightRoute;
import pt.isep.sidis.airports.flightroutes.domain.RouteStatus;
import pt.isep.sidis.airports.flightroutes.repositories.FlightRouteRepository;
import pt.isep.sidis.airports.unicorns.domain.UnicornMoodEntity;
import pt.isep.sidis.airports.unicorns.dto.UnicornMoodDTO;
import pt.isep.sidis.airports.unicorns.repositories.UnicornMoodRepository;

import java.time.Instant;
import java.util.*;

/**
 * Persistent CQRS Query Projection Service for Airplane Moods.
 * Computes mood dynamically from REAL domain data (Airports, Certifications, and Flight Routes):
 * - Persisted in the dedicated 'unicorn_airplane_moods' table (survives restarts).
 * - Decoupled from transactional write models.
 * - Updated asynchronously via domain events (CQRS Read Projection).
 */
@Service
public class UnicornMoodQueryService {

    private static final Logger log = LoggerFactory.getLogger(UnicornMoodQueryService.class);

    private final UnicornMoodRepository moodRepository;
    private final AirportRepository airportRepository;
    private final FlightRouteRepository flightRouteRepository;

    public UnicornMoodQueryService(UnicornMoodRepository moodRepository,
                                  AirportRepository airportRepository,
                                  FlightRouteRepository flightRouteRepository) {
        this.moodRepository = moodRepository;
        this.airportRepository = airportRepository;
        this.flightRouteRepository = flightRouteRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        refreshProjectionFromDomain();
    }

    @Transactional
    public void refreshProjectionFromDomain() {
        log.info("Computing CQRS Unicorn airplane mood projection from actual domain state");

        List<Airport> airports = airportRepository.findAll();
        List<FlightRoute> routes = flightRouteRepository.findAll();

        Set<String> certifiedModels = new LinkedHashSet<>(List.of("A320neo", "737 MAX", "A330-900neo", "Embraer 190"));
        Map<String, String> modelToAirport = new HashMap<>();
        modelToAirport.put("A320neo", "LIS");
        modelToAirport.put("737 MAX", "OPO");
        modelToAirport.put("A330-900neo", "FAO");
        modelToAirport.put("Embraer 190", "LIS");

        for (Airport airport : airports) {
            String iata = airport.getIataCode().getCode();
            if (airport.getCertifications() != null) {
                for (AirplaneCertification cert : airport.getCertifications()) {
                    certifiedModels.add(cert.getModelName());
                    modelToAirport.put(cert.getModelName(), iata);
                }
            }
        }

        long activeRoutesCount = routes.stream().filter(r -> r.getRouteStatus() == RouteStatus.ACTIVE).count();

        for (String model : certifiedModels) {
            String baseAirport = modelToAirport.getOrDefault(model, "LIS");
            Airport airport = airports.stream()
                    .filter(a -> a.getIataCode().getCode().equalsIgnoreCase(baseAirport))
                    .findFirst()
                    .orElse(null);

            boolean isOperational = airport == null || airport.getStatus() == Status.OPERATIONAL;

            String mood = isOperational && activeRoutesCount > 0 ? "ECSTATIC" : (isOperational ? "HAPPY" : "MAINTENANCE_BLUES");
            double vibeScore = isOperational && activeRoutesCount > 0 ? 9.8 : (isOperational ? 8.5 : 5.0);
            boolean sparkles = isOperational;
            String message = isOperational
                    ? "Operating smoothly on " + activeRoutesCount + " active routes with certified base at " + baseAirport + "."
                    : "Temporarily grounded or limited at " + baseAirport + " due to airport maintenance.";

            String reg = generateRegistration(model);

            UnicornMoodEntity entity = UnicornMoodEntity.builder()
                    .airplaneModel(model)
                    .registration(reg)
                    .mood(mood)
                    .vibeScore(vibeScore)
                    .statusMessage(message)
                    .lastKnownAirport(baseAirport)
                    .sparklesEnabled(sparkles)
                    .cqrsRole("CQRS Read Projection (Event-driven persistent view)")
                    .lastUpdated(Instant.now())
                    .build();

            moodRepository.save(entity);
        }

        log.info("Persistent unicorn airplane mood projection initialized with {} models", moodRepository.count());
    }

    @Transactional(readOnly = true)
    public List<UnicornMoodDTO> getAllMoods() {
        return moodRepository.findAll().stream()
                .map(UnicornMoodEntity::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UnicornMoodDTO> getMoodByModel(String model) {
        if (model == null || model.isBlank()) return Optional.empty();
        return moodRepository.findById(model)
                .or(() -> moodRepository.findByRegistrationIgnoreCase(model))
                .map(UnicornMoodEntity::toDTO);
    }

    @Transactional
    public void recordRouteActivity(String routeId, String origin, String destination) {
        log.info("Projecting unicorn mood update from route activity: {} ({} -> {})", routeId, origin, destination);
        moodRepository.findById("A320neo").ifPresent(entity -> {
            entity.setLastKnownAirport(destination);
            entity.setMood("ECSTATIC");
            entity.setVibeScore(10.0);
            entity.setStatusMessage("Completed flight on " + routeId + " arriving at " + destination + " with rainbow trailing.");
            entity.setSparklesEnabled(true);
            entity.setLastUpdated(Instant.now());
            moodRepository.save(entity);
        });
    }

    @Transactional
    public void recordAirportStatus(String iata, String status) {
        log.info("Projecting unicorn mood update from airport status: {} -> {}", iata, status);
        boolean isProblematic = "CLOSED".equalsIgnoreCase(status) || "UNDER_MAINTENANCE".equalsIgnoreCase(status);

        List<UnicornMoodEntity> matching = moodRepository.findAll().stream()
                .filter(e -> iata.equalsIgnoreCase(e.getLastKnownAirport()))
                .toList();

        for (UnicornMoodEntity entity : matching) {
            if (isProblematic) {
                entity.setMood("MAINTENANCE_BLUES");
                entity.setVibeScore(4.5);
                entity.setStatusMessage("Grounded at " + iata + " due to operational maintenance status.");
                entity.setSparklesEnabled(false);
            } else {
                entity.setMood("ECSTATIC");
                entity.setVibeScore(9.7);
                entity.setStatusMessage("Operational clearance granted at " + iata + ".");
                entity.setSparklesEnabled(true);
            }
            entity.setLastUpdated(Instant.now());
            moodRepository.save(entity);
        }
    }

    @Transactional
    public void recordCertification(String iata, String model) {
        log.info("Projecting unicorn mood from new airport certification: {} at {}", model, iata);
        UnicornMoodEntity entity = moodRepository.findById(model).orElseGet(() ->
                UnicornMoodEntity.builder()
                        .airplaneModel(model)
                        .registration(generateRegistration(model))
                        .build()
        );
        entity.setLastKnownAirport(iata);
        entity.setMood("ECSTATIC");
        entity.setVibeScore(9.9);
        entity.setStatusMessage("Newly certified at " + iata + "! Ready for commercial departures.");
        entity.setSparklesEnabled(true);
        entity.setCqrsRole("CQRS Read Projection (Event-driven persistent view)");
        entity.setLastUpdated(Instant.now());
        moodRepository.save(entity);
    }

    private String generateRegistration(String model) {
        return switch (model.toUpperCase()) {
            case "A320NEO" -> "CS-TND";
            case "737 MAX", "B737" -> "CS-TNK";
            case "A330-900NEO", "A330" -> "CS-TUQ";
            case "EMBRAER 190", "E190" -> "CS-TPO";
            default -> "CS-" + Math.abs(model.hashCode() % 900 + 100);
        };
    }
}
