package pt.isep.sidis.airports.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "aisafe.events";
    public static final String DLX_NAME = "aisafe.dlx";
    public static final String DLQ_NAME = "airports.events.dlq";

    public static final String ROUTING_KEY_AIRPORT_CREATED = "airport.created";
    public static final String ROUTING_KEY_AIRPORT_STATUS = "airport.status.changed";
    public static final String ROUTING_KEY_AIRPORT_CERTIFICATION = "airport.certification.added";
    public static final String ROUTING_KEY_ROUTE_CREATED = "route.created";
    public static final String ROUTING_KEY_ROUTE_UPDATED = "route.updated";
    public static final String ROUTING_KEY_ROUTE_DEACTIVATED = "route.deactivated";

    @Bean
    public TopicExchange aisafeExchange(@Value("${aisafe.rabbitmq.exchange:${sidis.rabbitmq.exchange:" + EXCHANGE_NAME + "}}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_NAME);
    }

    /**
     * Dedicated queue per replica instance (Publish/Subscribe pattern).
     * Configured with DLX, DLQ routing, message TTL, and max-length to fulfill Point 5.
     */
    @Bean
    public Queue replicaQueue(@Value("${airports.instance-name:instance1}") String instanceName) {
        return QueueBuilder.durable("airports.events." + instanceName)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .withArgument("x-max-length", 1000)
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public Binding bindingAirportEvents(Queue replicaQueue, TopicExchange aisafeExchange) {
        return BindingBuilder.bind(replicaQueue).to(aisafeExchange).with("airport.#");
    }

    @Bean
    public Binding bindingRouteEvents(Queue replicaQueue, TopicExchange aisafeExchange) {
        return BindingBuilder.bind(replicaQueue).to(aisafeExchange).with("route.#");
    }

    @Bean
    public Binding bindingAircraftEvents(Queue replicaQueue, TopicExchange aisafeExchange) {
        return BindingBuilder.bind(replicaQueue).to(aisafeExchange).with("aircraft.created");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
