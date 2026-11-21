package pt.isep.sidis.airports.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
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

    @Autowired
    private ObjectMapper objectMapper;

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
    void testReplicaEventListenerProcessesReplication() throws Exception {
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

        byte[] body = objectMapper.writeValueAsBytes(peerEvent);
        Message message = new Message(body);
        replicaEventListener.handleMessage(message, "airport.created");

        // Verify that the local database now has Santa Maria airport asynchronously replicated
        Optional<Airport> replicated = airportRepository.findByIataCode_Code("SMA");
        assertTrue(replicated.isPresent());
        assertEquals("Santa Maria Airport", replicated.get().getName());
    }

    @Test
    void testReplicaEventListenerRejectsMalformedJsonDirectlyToDlq() {
        // Point 3: Malformed JSON must throw AmqpRejectAndDontRequeueException (goes straight to DLQ)
        byte[] malformedBody = "{ invalid_json: true ".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Message message = new Message(malformedBody);

        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                replicaEventListener.handleMessage(message, "airport.created")
        );
    }

    @Test
    void testReplicaEventListenerFailsOnInvalidAirportUpdatedHours() throws Exception {
        // Ensure SMA airport exists first
        AirportCreatedEvent peerEvent = new AirportCreatedEvent(
                "instance2", "SMA", "Santa Maria Airport", "Vila do Porto", "Portugal", "OPERATIONAL", List.of("A320neo")
        );
        replicaEventListener.handleMessage(new Message(objectMapper.writeValueAsBytes(peerEvent)), "airport.created");

        // Point 2: airport.updated with invalid hours must NOT be silently swallowed; it must throw so it triggers DLQ
        pt.isep.sidis.airports.events.AirportUpdatedEvent invalidHoursEvent =
                new pt.isep.sidis.airports.events.AirportUpdatedEvent("instance2", "SMA", "Santa Maria Airport",
                        "Vila do Porto", "Portugal", "OPERATIONAL", List.of(), "25:99", "18:00", List.of());

        Message message = new Message(objectMapper.writeValueAsBytes(invalidHoursEvent));

        assertThrows(RuntimeException.class, () ->
                replicaEventListener.handleMessage(message, "airport.updated")
        );
    }

    @Autowired
    private pt.isep.sidis.airports.unicorns.services.UnicornMoodQueryService unicornMoodQueryService;

    @Test
    void testUnicornMoodProjectionUpdatesOnAircraftStatusChanged() throws Exception {
        // Point 4: aircraft.created records registration and model
        String createJson = """
                {
                  "registration": "CS-TNA",
                  "modelCode": "A320neo",
                  "status": "AVAILABLE"
                }
                """;
        replicaEventListener.handleMessage(new Message(createJson.getBytes()), "aircraft.created");

        pt.isep.sidis.airports.unicorns.dto.UnicornMoodDTO dto = unicornMoodQueryService.getMoodByModel("CS-TNA").orElseThrow();
        assertEquals("ECSTATIC", dto.getMood());
        assertTrue(dto.getSparklesEnabled());

        // Now test aircraft.status.changed with MAINTENANCE
        String maintJson = """
                {
                  "registration": "CS-TNA",
                  "modelCode": "A320neo",
                  "status": "UNDER_MAINTENANCE"
                }
                """;
        replicaEventListener.handleMessage(new Message(maintJson.getBytes()), "aircraft.status.changed");

        pt.isep.sidis.airports.unicorns.dto.UnicornMoodDTO maintDto = unicornMoodQueryService.getMoodByModel("CS-TNA").orElseThrow();
        assertEquals("MAINTENANCE_BLUES", maintDto.getMood());
        assertFalse(maintDto.getSparklesEnabled());
    }
}
