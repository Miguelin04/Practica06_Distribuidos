package com.fleet.monitor.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * SensorSimulator — APE-05 Flota Logística
 *
 * Reemplaza simulador.py. Publica telemetría de 3 vehículos cada 5 segundos
 * en los topics MQTT jerárquicos definidos por la guía:
 *
 *   flota/{id}/gps          → GPS + velocidad
 *   flota/{id}/temperatura  → temperatura de la carga (-5°C a 8°C)
 *   flota/{id}/combustible  → nivel de combustible (0–100%)
 *
 * @Scheduled(fixedDelay = 5000) → equivale a time.sleep(5) del simulador Python.
 *
 * Coordenadas base Guayaquil: lat=-2.1709, lng=-79.9224
 * El offset aleatorio de ±0.5° mantiene los vehículos en el área metropolitana.
 */
@Component
public class SensorSimulator {

    // ── Vehículos a simular ───────────────────────────────────────────────────
    private static final List<String> VEHICULOS = List.of("VH-001", "VH-002", "VH-003");

    // ── Base de coordenadas Guayaquil (guía APE-05) ───────────────────────────
    private static final double BASE_LAT = -2.1709;
    private static final double BASE_LNG = -79.9224;

    // ── QoS 1 = "al menos una vez" (requerido por la guía) ───────────────────
    private static final int QOS = 1;

    private final MqttClient publisher;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Random rng = new Random();
    private int ciclo = 1;

    public SensorSimulator(@Qualifier("mqttPublisher") MqttClient publisher) {
        this.publisher = publisher;
    }

    /**
     * Publica un ciclo completo de telemetría cada 5 segundos.
     * fixedDelay asegura 5 s ENTRE finales de ciclos (igual que time.sleep(5)).
     */
    @Scheduled(fixedDelay = 5000)
    public void publicarCiclo() {
        System.out.printf("%n%s%n  CICLO #%d — %s%n%s%n",
                "=".repeat(70), ciclo, LocalDateTime.now(), "=".repeat(70));

        for (String vid : VEHICULOS) {
            publicarGps(vid);
            publicarTemperatura(vid);
            publicarCombustible(vid);
            System.out.println("-".repeat(70));
        }
        ciclo++;
    }

    // ── Publicadores individuales ─────────────────────────────────────────────

    private void publicarGps(String vid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehiculo_id",    vid);
        payload.put("latitud",        round(BASE_LAT + rng.nextDouble() * 0.5 - 0.25, 6));
        payload.put("longitud",       round(BASE_LNG + rng.nextDouble() * 0.5 - 0.25, 6));
        payload.put("velocidad_kmh",  round(rng.nextDouble() * 120, 1));
        payload.put("timestamp",      LocalDateTime.now().toString());
        publicar("flota/" + vid + "/gps", payload, "GPS");
    }

    private void publicarTemperatura(String vid) {
        // Temperatura en rango -5°C a 8°C (cadena de frío — guía APE-05)
        double temp = round(-5.0 + rng.nextDouble() * 13.0, 1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehiculo_id",   vid);
        payload.put("temperatura_c", temp);
        payload.put("alerta",        temp > 4.0);   // umbral guía APE-05
        payload.put("timestamp",     LocalDateTime.now().toString());
        publicar("flota/" + vid + "/temperatura", payload, "TEMP");
    }

    private void publicarCombustible(String vid) {
        double nivel = round(rng.nextDouble() * 100.0, 1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehiculo_id", vid);
        payload.put("nivel_pct",   nivel);
        payload.put("alerta_bajo", nivel < 20.0);   // umbral guía APE-05
        payload.put("timestamp",   LocalDateTime.now().toString());
        publicar("flota/" + vid + "/combustible", payload, "COMB");
    }

    /** Serializa el mapa a JSON y publica en el topic dado. */
    private void publicar(String topic, Map<String, Object> data, String etiqueta) {
        try {
            String json = mapper.writeValueAsString(data);
            MqttMessage msg = new MqttMessage(json.getBytes());
            msg.setQos(QOS);
            publisher.publish(topic, msg);
            System.out.printf("[SIMULADOR] %-6s → %-40s %s%n", etiqueta, topic, json);
        } catch (Exception e) {
            System.err.println("[SIMULADOR] Error publicando en " + topic + ": " + e.getMessage());
        }
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
