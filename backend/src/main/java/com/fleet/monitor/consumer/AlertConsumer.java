package com.fleet.monitor.consumer;

import com.fleet.monitor.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AlertConsumer — APE-06 Flota Logística
 *
 * Consumidor AMQP que implementa el patrón de routing de alertas:
 *
 *   cola.temperatura (temp.alert) → detecta si alerta=true → reenvía a cola.notificaciones
 *   cola.combustible (fuel.routing)→ detecta si alerta_bajo=true → reenvía a cola.notificaciones
 *
 * Este es el componente que demuestra el routing selectivo de RabbitMQ:
 * el bridge ya filtró y solo mandó alertas, pero AlertConsumer hace el
 * segundo nivel de enrutamiento hacia cola.notificaciones.
 *
 * Logs requeridos por la rúbrica:
 *   [ALERT CONSUMER] ALERTA TEMPERATURA — ...
 *   [ALERT CONSUMER] ALERTA COMBUSTIBLE — ...
 */
@Component
public class AlertConsumer {

    private final RabbitTemplate rabbitTemplate;

    public AlertConsumer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // ── Consumidor de temperaturas ────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.COLA_TEMPERATURA)
    public void consumirTemperatura(Map<String, Object> datos) {
        try {
            String vehiculoId  = (String) datos.getOrDefault("vehiculo_id", "DESCONOCIDO");
            double temperatura = toDouble(datos.get("temperatura_c"));
            boolean esAlerta   = Boolean.TRUE.equals(datos.get("alerta"));

            if (esAlerta) {
                // ── Log requerido por la rúbrica ──────────────────────────────
                System.out.printf(
                    "[ALERT CONSUMER] ALERTA TEMPERATURA — %s | temp=%.1f°C FUERA DE RANGO%n",
                    vehiculoId, temperatura);

                // Reenviar a cola.notificaciones
                Map<String, Object> notif = Map.of(
                    "vehiculo_id", vehiculoId,
                    "mensaje", String.format("Temperatura crítica en %s: %.1f°C", vehiculoId, temperatura)
                );
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_FLEET, RabbitMQConfig.RK_TEMP, notif);

                System.out.printf(
                    "[ALERT CONSUMER] Notificación enviada → cola.notificaciones para %s%n",
                    vehiculoId);
            } else {
                System.out.printf(
                    "[ALERT CONSUMER] Temperatura normal — %s | temp=%.1f°C%n",
                    vehiculoId, temperatura);
            }
        } catch (Exception e) {
            System.err.println("[ALERT CONSUMER] Error temperatura: " + e.getMessage());
        }
    }

    // ── Consumidor de combustible ─────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.COLA_COMBUSTIBLE)
    public void consumirCombustible(Map<String, Object> datos) {
        try {
            String vehiculoId = (String) datos.getOrDefault("vehiculo_id", "DESCONOCIDO");
            double nivel      = toDouble(datos.get("nivel_pct"));
            boolean esAlerta  = Boolean.TRUE.equals(datos.get("alerta_bajo"));

            if (esAlerta) {
                // ── Log requerido por la rúbrica ──────────────────────────────
                System.out.printf(
                    "[ALERT CONSUMER] ALERTA COMBUSTIBLE — %s | nivel=%.1f%% BAJO 20%%%n",
                    vehiculoId, nivel);

                // Reenviar a cola.notificaciones
                Map<String, Object> notif = Map.of(
                    "vehiculo_id", vehiculoId,
                    "mensaje", String.format("Combustible bajo en %s: %.1f%%", vehiculoId, nivel)
                );
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_FLEET, RabbitMQConfig.RK_FUEL, notif);

                System.out.printf(
                    "[ALERT CONSUMER] Notificación combustible → cola.notificaciones para %s%n",
                    vehiculoId);
            } else {
                System.out.printf(
                    "[ALERT CONSUMER] Combustible normal — %s | nivel=%.1f%%%n",
                    vehiculoId, nivel);
            }
        } catch (Exception e) {
            System.err.println("[ALERT CONSUMER] Error combustible: " + e.getMessage());
        }
    }

    private static double toDouble(Object val) {
        if (val == null)             return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
