package edu.ape006.fleetmonitor.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

/**
 * RabbitMQConfig — APE-006 Flota Logística
 *
 * Define el exchange, las colas y los bindings que conectan ambos.
 * Se crea PRIMERO porque GpsConsumer y AlertConsumer dependen de las constantes
 * aquí definidas para escuchar las colas correctas.
 *
 * Arquitectura:
 * exchange.fleet (Direct)
 * ├─ gps.routing → cola.gps
 * ├─ temp.alert → cola.temperatura
 * ├─ temp.alert → cola.notificaciones (fan-out de alertas)
 * └─ fuel.routing → cola.combustible
 */
@Configuration
public class RabbitMQConfig {

    // ── Nombre del exchange ────────────────────────────────────────────────
    public static final String EXCHANGE_FLEET = "exchange.fleet";

    // ── Nombres de las colas ───────────────────────────────────────────────
    public static final String COLA_GPS = "cola.gps";
    public static final String COLA_TEMPERATURA = "cola.temperatura";
    public static final String COLA_COMBUSTIBLE = "cola.combustible";
    public static final String COLA_NOTIFICACIONES = "cola.notificaciones";

    // ── Routing keys ──────────────────────────────────────────────────────
    public static final String RK_GPS = "gps.routing";
    public static final String RK_TEMP = "temp.alert";
    public static final String RK_FUEL = "fuel.routing";

    // ── Exchange Direct durable ───────────────────────────────────────────
    @Bean
    public DirectExchange fleetExchange() {
        return new DirectExchange(EXCHANGE_FLEET, /* durable */ true, /* autoDelete */ false);
    }

    // ── Colas durables ────────────────────────────────────────────────────
    @Bean
    public Queue colaGps() {
        return QueueBuilder.durable(COLA_GPS).build();
    }

    @Bean
    public Queue colaTemperatura() {
        return QueueBuilder.durable(COLA_TEMPERATURA).build();
    }

    @Bean
    public Queue colaCombustible() {
        return QueueBuilder.durable(COLA_COMBUSTIBLE).build();
    }

    @Bean
    public Queue colaNotificaciones() {
        return QueueBuilder.durable(COLA_NOTIFICACIONES).build();
    }

    // ── Bindings: cola ←→ exchange con routing key ────────────────────────
    @Bean
    public Binding bindingGps(Queue colaGps, DirectExchange fleetExchange) {
        return BindingBuilder.bind(colaGps).to(fleetExchange).with(RK_GPS);
    }

    @Bean
    public Binding bindingTemperatura(Queue colaTemperatura, DirectExchange fleetExchange) {
        return BindingBuilder.bind(colaTemperatura).to(fleetExchange).with(RK_TEMP);
    }

    @Bean
    public Binding bindingCombustible(Queue colaCombustible, DirectExchange fleetExchange) {
        return BindingBuilder.bind(colaCombustible).to(fleetExchange).with(RK_FUEL);
    }

    @Bean
    public Binding bindingNotificaciones(Queue colaNotificaciones, DirectExchange fleetExchange) {
        // Las notificaciones también escuchan la routing key de temperatura (alertas
        // críticas)
        return BindingBuilder.bind(colaNotificaciones).to(fleetExchange).with(RK_TEMP);
    }

    // ── Conversor JSON ────────────────────────────────────────────────────
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @SuppressWarnings("null")
    @Bean
    public RabbitTemplate rabbitTemplate(@NonNull ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
