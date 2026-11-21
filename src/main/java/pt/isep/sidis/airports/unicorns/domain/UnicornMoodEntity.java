package pt.isep.sidis.airports.unicorns.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pt.isep.sidis.airports.unicorns.dto.UnicornMoodDTO;

import java.time.Instant;

/**
 * Persistent CQRS Read Projection Entity for Aircraft Moods.
 * Saved in dedicated table to survive server restarts and updated via domain events.
 */
@Entity
@Table(name = "unicorn_airplane_moods")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnicornMoodEntity {

    @Id
    @Column(nullable = false, unique = true)
    private String airplaneModel;

    private String registration;

    @Column(nullable = false)
    private String mood;

    private Double vibeScore;

    @Column(length = 1000)
    private String statusMessage;

    private String lastKnownAirport;

    private Boolean sparklesEnabled;

    private String cqrsRole;

    private Instant lastUpdated;

    public UnicornMoodDTO toDTO() {
        return UnicornMoodDTO.builder()
                .airplaneModel(airplaneModel)
                .registration(registration)
                .mood(mood)
                .vibeScore(vibeScore)
                .statusMessage(statusMessage)
                .lastKnownAirport(lastKnownAirport)
                .sparklesEnabled(sparklesEnabled)
                .cqrsRole(cqrsRole)
                .lastUpdated(lastUpdated)
                .build();
    }
}
