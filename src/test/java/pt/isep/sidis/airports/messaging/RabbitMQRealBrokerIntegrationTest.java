package pt.isep.sidis.airports.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pt.isep.sidis.airports.airportmanagement.domain.Airport;
import pt.isep.sidis.airports.airportmanagement.repositories.AirportRepository;
import pt.isep.sidis.airports.events.AirportCreatedEvent;
import pt.isep.sidis.airports.events.FlightRouteCreatedEvent;
import pt.isep.sidis.airports.flightroutes.domain.FlightRoute;
import pt.isep.sidis.airports.flightroutes.domain.RouteId;
import pt.isep.sidis.airports.flightroutes.repositories.FlightRouteRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test with a REAL RabbitMQ message broker container (Testcontainers).
 * Fulfills Point 7 of Assignment 2 Review:
 * - Runs a real RabbitMQ 3 Management container.
 * - Publishes serialized JSON events to the 'aisafe.events' topic exchange.
 * - Broker routes messages to the replica's durable queue.
 * - ReplicaEventListener receives the message, parses the payload based on the routing key header,
 *   and replicates the data into the local database partition.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
public class RabbitMQRealBrokerIntegrationTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void configureRabbitMQProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
        registry.add("aisafe.rabbitmq.exchange", () -> "aisafe.events");
        registry.add("sidis.rabbitmq.exchange", () -> "aisafe.events");
        registry.add("airports.instance-name", () -> "testreplica");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private FlightRouteRepository flightRouteRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReplicateAirportAndRouteThroughRealRabbitMQBroker() throws Exception {
        // 1. Publish AirportCreatedEvent for origin airport (FLW - Flores)
        AirportCreatedEvent originEvent = new AirportCreatedEvent(
                "instance2", "FLW", "Flores Airport", "Santa Cruz das Flores",
                "Portugal", "OPERATIONAL", List.of("A320neo")
        );

        String originJson = objectMapper.writeValueAsString(originEvent);
        Message originMsg = MessageBuilder.withBody(originJson.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("eventId", originEvent.getEventId())
                .setHeader("eventType", originEvent.getEventType())
                .build();

        rabbitTemplate.send("aisafe.events", "airport.created", originMsg);

        // 2. Publish AirportCreatedEvent for destination airport (PDL - Ponta Delgada)
        AirportCreatedEvent destEvent = new AirportCreatedEvent(
                "instance2", "PDL", "Ponta Delgada Airport", "Ponta Delgada",
                "Portugal", "OPERATIONAL", List.of("A320neo")
        );

        String destJson = objectMapper.writeValueAsString(destEvent);
        Message destMsg = MessageBuilder.withBody(destJson.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("eventId", destEvent.getEventId())
                .setHeader("eventType", destEvent.getEventType())
                .build();

        rabbitTemplate.send("aisafe.events", "airport.created", destMsg);

        // 3. Verify airports were received and persisted by ReplicaEventListener
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Airport> flw = airportRepository.findByIataCode_Code("FLW");
            Optional<Airport> pdl = airportRepository.findByIataCode_Code("PDL");
            assertThat(flw).isPresent();
            assertThat(pdl).isPresent();
        });

        // 4. Publish FlightRouteCreatedEvent connecting FLW and PDL
        FlightRouteCreatedEvent routeEvent = new FlightRouteCreatedEvent(
                "instance2", "route-flw-pdl", "FLW", "PDL",
                380.0, 450.0, 150, 50, "ACTIVE"
        );

        String routeJson = objectMapper.writeValueAsString(routeEvent);
        Message routeMsg = MessageBuilder.withBody(routeJson.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("eventId", routeEvent.getEventId())
                .setHeader("eventType", routeEvent.getEventType())
                .build();

        rabbitTemplate.send("aisafe.events", "route.created", routeMsg);

        // 5. Verify flight route was received and persisted by ReplicaEventListener
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<FlightRoute> route = flightRouteRepository.findById(new RouteId("route-flw-pdl"));
            assertThat(route).isPresent();
            assertThat(route.get().getDistance()).isEqualTo(380.0);
            assertThat(route.get().getRouteRequirement().getMinRangeRequired()).isEqualTo(450.0);
            assertThat(route.get().getRouteRequirement().getMinCapacityRequired()).isEqualTo(150);
        });
    }
}
