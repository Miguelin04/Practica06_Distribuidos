package com.fleet.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FleetMonitorApplication — APE-05/06 Flota Logística
 *
 * Punto de entrada de la aplicación Spring Boot.
 * Integra en un solo proceso:
 *   - SensorSimulator   → reemplaza simulador.py  (publica en Mosquitto vía MQTT)
 *   - MqttSubscriber    → reemplaza suscriptor.py  (persiste en H2 con JPA)
 *   - MqttBridge        → reemplaza bridge.py      (reenvía alertas a RabbitMQ)
 *   - GpsConsumer       → consume cola.gps de RabbitMQ
 *   - AlertConsumer     → consume cola.temperatura y reenvía a cola.notificaciones
 *   - FleetController   → expone REST API en puerto 8080
 *
 * @EnableScheduling habilita la anotación @Scheduled usada por SensorSimulator.
 */
@SpringBootApplication
@EnableScheduling
public class FleetMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FleetMonitorApplication.class, args);
    }
}
