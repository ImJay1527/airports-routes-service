package pt.isep.sidis.airports.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    public static final String AIRPORT_ROUTING_KEY_PREFIX = "airport.";
    public static final String ROUTE_ROUTING_KEY_PREFIX = "route.";

    @Value("${sidis.rabbitmq.exchange:sidis.airports.exchange}")
    private String exchangeName;

    @Value("${airports.instance-name:instance1}")
    private String instanceName;

    @Bean
    public TopicExchange airportsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue replicaQueue() {
        // Unique durable queue for this replica instance
        return new Queue("sidis.airports.replica." + instanceName, true, false, false);
    }

    @Bean
    public Binding bindingAirportEvents(Queue replicaQueue, TopicExchange airportsExchange) {
        return BindingBuilder.bind(replicaQueue).to(airportsExchange).with("airport.*");
    }

    @Bean
    public Binding bindingRouteEvents(Queue replicaQueue, TopicExchange airportsExchange) {
        return BindingBuilder.bind(replicaQueue).to(airportsExchange).with("route.*");
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
