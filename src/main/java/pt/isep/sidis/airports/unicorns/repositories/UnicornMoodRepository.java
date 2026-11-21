package pt.isep.sidis.airports.unicorns.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.isep.sidis.airports.unicorns.domain.UnicornMoodEntity;

import java.util.Optional;

@Repository
public interface UnicornMoodRepository extends JpaRepository<UnicornMoodEntity, String> {
    Optional<UnicornMoodEntity> findByRegistrationIgnoreCase(String registration);
}
