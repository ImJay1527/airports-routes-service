package pt.isep.sidis.airports.cqrs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import pt.isep.sidis.airports.airportmanagement.api.dto.CreateAirportRequestDTO;
import pt.isep.sidis.airports.airportmanagement.api.dto.CreateRunwayRequestDTO;
import pt.isep.sidis.airports.airportmanagement.domain.Airport;
import pt.isep.sidis.airports.airportmanagement.domain.Orientation;
import pt.isep.sidis.airports.airportmanagement.domain.Status;
import pt.isep.sidis.airports.airportmanagement.repositories.AirportRepository;
import pt.isep.sidis.airports.cqrs.commands.AirportCommandHandler;
import pt.isep.sidis.airports.cqrs.commands.CreateAirportCommand;
import pt.isep.sidis.airports.cqrs.commands.UpdateAirportStatusCommand;
import pt.isep.sidis.airports.cqrs.listeners.ReplicaEventListener;
import pt.isep.sidis.airports.events.AirportCreatedEvent;
import pt.isep.sidis.airports.messaging.EventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
class CqrsEventReplicationTest {

    @Autowired
    private AirportCommandHandler airportCommandHandler;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private ReplicaEventListener replicaEventListener;

    @MockBean
    private EventPublisher eventPublisher;

    @Test
    void testAirportCommandHandlerPublishesEvent() {
        CreateAirportRequestDTO dto = new CreateAirportRequestDTO();
        dto.setIataCode("VRL");
        dto.setName("Vila Real Airport");
        dto.setCity("Vila Real");
        dto.setCountry("Portugal");
        dto.setRegion("North");
        dto.setTimezone("UTC+00:00");
        CreateRunwayRequestDTO runway = new CreateRunwayRequestDTO();
        runway.setName("18/36");
        runway.setLength(1500.0);
        runway.setOrientation(Orientation.N);
        dto.setRunways(List.of(runway));

        Airport created = airportCommandHandler.handle(new CreateAirportCommand(dto));
        assertNotNull(created);
        assertEquals("VRL", created.getIataCode().getCode());

        // Verify that domain event was published to RabbitMQ topic
        verify(eventPublisher).publish(eq("airport.created"), any(AirportCreatedEvent.class));

        // Test update status with proper routing key
        Airport updated = airportCommandHandler.handle(new UpdateAirportStatusCommand("VRL", "UNDER_MAINTENANCE", created.getVersion()));
        assertEquals(Status.UNDER_MAINTENANCE, updated.getStatus());
        verify(eventPublisher).publish(eq("airport.status.changed"), any());
    }

    @Test
    void testReplicaEventListenerProcessesReplication() {
        // Simulate an event arriving from peer instance2 for a new airport
        AirportCreatedEvent peerEvent = new AirportCreatedEvent(
                "instance2",
                "SMA",
                "Santa Maria Airport",
                "Vila do Porto",
                "Portugal",
                "OPERATIONAL",
                List.of("A320neo")
        );

        replicaEventListener.handleDomainEvent(peerEvent);

        // Verify that the local database now has Santa Maria airport asynchronously replicated
        Optional<Airport> replicated = airportRepository.findByIataCode_Code("SMA");
        assertTrue(replicated.isPresent());
        assertEquals("Santa Maria Airport", replicated.get().getName());
    }
}
