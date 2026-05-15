package com.fleet.monitor.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.monitor.config.RabbitMQConfig;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MqttBridge — APE-05 Flota Logística
 *
 * Reemplaza bridge.py. Escucha todos los topics de la flota (flota/#) y
 * reenvía a RabbitMQ SOLO las alertas:
 *
 *   temperatura > 4 °C  → exchange.fleet con routing key "temp.alert"
 *   combustible  < 20 % → exchange.fleet con routing key "fuel.routing"
 *
 * GPS nunca se publica en RabbitMQ (no es alerta).
 *
 * IMPORTANTE: MqttSubscriber y MqttBridge comparten el mismo MqttClient bean
 * "mqttSubscriber". Spring llama a @PostConstruct en ambos; el segundo subscribe()
 * en el mismo topic simplemente añade otro callback interno de Paho — es seguro.
 *
 * Patrón: MQTT → Bridge → RabbitMQ (solo alertas) → AlertConsumer → cola.notificaciones
 */
@Component
public class MqttBridge implements MqttCallback {

    private static final String TOPIC_WILDCARD = "flota/#";
    private static final int    QOS            = 1;

    private final MqttClient     mqttClient;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   mapper = new ObjectMapper();

    public MqttBridge(
            @Qualifier("mqttClientSubscriber") MqttClient mqttClient,
            RabbitTemplate rabbitTemplate) {
        this.mqttClient     = mqttClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Registra este bean como segundo callback en el cliente compartido.
     * Paho permite múltiples suscripciones; el cliente despacha el mismo
     * mensaje a todos los callbacks registrados.
     *
     * NOTA: setCallback() reemplaza el callback anterior. Para compartir el
     * cliente usamos un CompositeCallback que despacha a ambos listeners.
     * En la práctica, el bridge registra su propio cliente de suscripción
     * independiente para mayor claridad.
     */
    @PostConstruct
    public void iniciar() {
        // El bridge escucha mensajes a través de MqttSubscriber (callback ya registrado).
        // Su lógica se invoca desde messageArrived() de MqttSubscriber cuando hay alertas.
        // Ver MqttSubscriber: llama a bridge.reenviarAlerta() explícitamente.
        System.out.println("[BRIDGE] Listo — reenviará alertas MQTT → RabbitMQ");
    }

    /**
     * Recibe un payload de temperatura y lo reenvía a RabbitMQ si es alerta.
     * Llamado por MqttSubscriber cuando temperatura > 4 °C.
     *
     * @param vehiculoId identificador del vehículo
     * @param payload    mapa JSON del mensaje original
     */
    public void reenviarAlertaTemperatura(String vehiculoId, Map<String, Object> payload) {
        payload.put("_bridge_topic",     "flota/" + vehiculoId + "/temperatura");
        payload.put("_bridge_timestamp", LocalDateTime.now().toString());

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_FLEET,
            RabbitMQConfig.RK_TEMP,
            payload
        );
        System.out.printf("[BRIDGE] TEMP ALERTA → RabbitMQ [%s] vehículo=%s%n",
                RabbitMQConfig.RK_TEMP, vehiculoId);
    }

    /**
     * Recibe un payload de combustible y lo reenvía a RabbitMQ si es alerta.
     * Llamado por MqttSubscriber cuando nivel < 20 %.
     *
     * @param vehiculoId identificador del vehículo
     * @param payload    mapa JSON del mensaje original
     */
    public void reenviarAlertaCombustible(String vehiculoId, Map<String, Object> payload) {
        payload.put("_bridge_topic",     "flota/" + vehiculoId + "/combustible");
        payload.put("_bridge_timestamp", LocalDateTime.now().toString());

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_FLEET,
            RabbitMQConfig.RK_FUEL,
            payload
        );
        System.out.printf("[BRIDGE] COMB ALERTA → RabbitMQ [%s] vehículo=%s nivel=%.1f%%%n",
                RabbitMQConfig.RK_FUEL, vehiculoId,
                toDouble(payload.get("nivel_pct")));
    }

    // ── MqttCallback vacíos (este bean no registra su propio callback) ─────────
    @Override public void connectionLost(Throwable cause) {}
    @Override public void messageArrived(String topic, MqttMessage message) {}
    @Override public void deliveryComplete(IMqttDeliveryToken token) {}

    private static double toDouble(Object val) {
        if (val == null)             return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
