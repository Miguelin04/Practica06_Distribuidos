package com.fleet.monitor.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MqttConfig — APE-05 Flota Logística
 *
 * Configura y expone dos beans MqttClient de Eclipse Paho:
 *
 *   mqttPublisher  → usado por SensorSimulator para publicar telemetría
 *   mqttSubscriber → usado por MqttSubscriber y MqttBridge para suscribirse
 *
 * Necesitamos dos clientes independientes porque un cliente Paho no puede
 * publicar y suscribirse simultáneamente con loop_start (bloquea el hilo).
 * En Java, cada MqttClient tiene su propio hilo de callback.
 *
 * Configuración en application.properties:
 *   mqtt.broker.url  = tcp://localhost:1883
 */
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    /**
     * MqttClient para el simulador (publicador).
     * client-id único para evitar conflictos con el suscriptor.
     */
    @Bean(name = "mqttPublisher", destroyMethod = "disconnect")
    public MqttClient mqttPublisher() throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, "fleet-publisher-spring");
        client.connect(defaultOptions());
        System.out.println("[MQTT CONFIG] Publisher conectado a " + brokerUrl);
        return client;
    }

    /**
     * MqttClient compartido por MqttSubscriber y MqttBridge.
     * Se conecta sin cleanSession=false para recibir mensajes QoS 1+ retenidos.
     */
    @Bean(name = "mqttClientSubscriber", destroyMethod = "disconnect")
    public MqttClient mqttSubscriberClient() throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, "fleet-subscriber-spring");
        client.connect(defaultOptions());
        System.out.println("[MQTT CONFIG] Subscriber conectado a " + brokerUrl);
        return client;
    }

    /** Opciones comunes: reconexión automática, keepAlive 60 s, QoS limpio. */
    private MqttConnectOptions defaultOptions() {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setKeepAliveInterval(60);
        opts.setAutomaticReconnect(true);
        opts.setConnectionTimeout(10);
        return opts;
    }
}
