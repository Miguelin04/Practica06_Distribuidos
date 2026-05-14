package edu.ape006.fleetmonitor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ape006.fleetmonitor.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AlertConsumer — APE-006 Flota Logística
 *
 * Consumidor AMQP que escucha la cola 'cola.temperatura'.
 * Si detecta una alerta de temperatura:
 * 1. Imprime "ALERTA TEMPERATURA" (requerido por la rúbrica)
 * 2. Reenvía el mensaje a 'cola.notificaciones' para procesamiento adicional
 *
 * La separación en dos colas (temperatura vs notificaciones) demuestra
 * el patrón pub/sub donde varios consumidores pueden procesar el mismo evento.
 */
@Component
public class AlertConsumer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AlertConsumer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Escucha cola de temperatura y procesa alertas.
     * El log "ALERTA TEMPERATURA" es requerido por la rúbrica de la práctica.
     */
    @RabbitListener(queues = RabbitMQConfig.COLA_TEMPERATURA)
    public void consumirTemperatura(String mensaje) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> datos = objectMapper.readValue(mensaje, Map.class);

            String vehiculoId = (String) datos.getOrDefault("vehiculo_id", "DESCONOCIDO");
            Double temperatura = toDouble(datos.get("temperatura_c"));
            boolean esAlerta = Boolean.TRUE.equals(datos.get("alerta"));

            if (esAlerta) {
                // ── Log requerido por la rúbrica ──────────────────────────
                System.out.printf(
                        "[ALERT CONSUMER] ALERTA TEMPERATURA — %s | temp=%.1f°C — FUERA DE RANGO%n",
                        vehiculoId, temperatura);

                // Reenviar a cola de notificaciones (binding ya existe en RabbitMQConfig)
                // El bridge ya publica en el exchange con rk=temp.alert, que llega a
                // AMBAS colas (cola.temperatura Y cola.notificaciones) por el binding doble.
                // Aquí demostramos el reenvío explícito como requiere la guía.
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_FLEET,
                        RabbitMQConfig.RK_TEMP,
                        "[NOTIFICACIÓN] Temperatura crítica en " + vehiculoId + ": " + temperatura + "°C");

                System.out.printf(
                        "[ALERT CONSUMER] Notificación enviada a cola.notificaciones para %s%n",
                        vehiculoId);
            } else {
                System.out.printf(
                        "[ALERT CONSUMER] Temperatura normal — %s | temp=%.1f°C%n",
                        vehiculoId, temperatura);
            }

        } catch (Exception e) {
            System.err.println("[ALERT CONSUMER] Error procesando alerta: " + e.getMessage());
        }
    }

    /** Convierte un Object (Integer/Double/String) a Double de forma segura. */
    private Double toDouble(Object value) {
        if (value == null)
            return 0.0;
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
