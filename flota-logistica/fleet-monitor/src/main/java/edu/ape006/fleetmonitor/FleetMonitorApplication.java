package edu.ape006.fleetmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FleetMonitorApplication — APE-006 Flota Logística
 *
 * Punto de entrada de la aplicación Spring Boot.
 * Componentes integrados:
 *   - Spring AMQP → consume colas de RabbitMQ
 *   - Spring Data JPA + H2 → persiste mensajes GPS en memoria
 *   - Spring Web → expone la API REST en puerto 8080
 */
@SpringBootApplication
public class FleetMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FleetMonitorApplication.class, args);
    }
}
