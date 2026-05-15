package com.fleet.monitor.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.monitor.model.GpsData;
import com.fleet.monitor.model.TempData;
import com.fleet.monitor.model.FuelData;
import com.fleet.monitor.repository.GpsRepository;
import com.fleet.monitor.repository.TempRepository;
import com.fleet.monitor.repository.FuelRepository;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MqttSubscriber — APE-05 Flota Logística
 *
 * Reemplaza suscriptor.py. Suscribe al wildcard "flota/#" y persiste cada
 * mensaje en las tres tablas JPA correspondientes (gps_data, temp_data, fuel_data).
 * Imprime alertas en consola cuando:
 *   - temperatura > 4 °C  → [ALERTA TEMP]
 *   - combustible  < 20 % → [ALERTA COMB]
 *
 * La persistencia usa H2 en lugar de SQLite pero los datos son equivalentes.
 * Las tablas se inicializan automáticamente por spring.jpa.hibernate.ddl-auto=update.
 */
@Component
public class MqttSubscriber implements MqttCallback {

    private static final String TOPIC_WILDCARD = "flota/#";
    private static final int    QOS            = 1;

    private final MqttClient     client;
    private final GpsRepository  gpsRepo;
    private final TempRepository tempRepo;
    private final FuelRepository fuelRepo;
    private final MqttBridge     bridge;
    private final ObjectMapper   mapper = new ObjectMapper();

    public MqttSubscriber(
            @Qualifier("mqttClientSubscriber") MqttClient client,
            GpsRepository  gpsRepo,
            TempRepository tempRepo,
            FuelRepository fuelRepo,
            @Lazy MqttBridge bridge) {
        this.client   = client;
        this.gpsRepo  = gpsRepo;
        this.tempRepo = tempRepo;
        this.fuelRepo = fuelRepo;
        this.bridge   = bridge;
    }

    /** Se ejecuta al levantar el contexto Spring: registra callback y suscribe. */
    @PostConstruct
    public void iniciar() throws MqttException {
        client.setCallback(this);
        client.subscribe(TOPIC_WILDCARD, QOS);
        System.out.println("[SUSCRIPTOR] Suscrito a '" + TOPIC_WILDCARD + "' — BD lista (H2)");
    }

    // ── Callbacks MqttCallback ────────────────────────────────────────────────

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("[SUSCRIPTOR] Conexión perdida: " + cause.getMessage());
    }

    /**
     * Procesa cada mensaje entrante según el tercer segmento del topic:
     *   flota/{vehiculoId}/gps          → persiste GpsData
     *   flota/{vehiculoId}/temperatura  → persiste TempData + alerta consola
     *   flota/{vehiculoId}/combustible  → persiste FuelData + alerta consola
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String json = new String(message.getPayload());
            @SuppressWarnings("unchecked")
            Map<String, Object> datos = mapper.readValue(json, Map.class);

            String[] partes = topic.split("/");
            if (partes.length < 3) return;

            String vehiculoId = partes[1];
            String sensor     = partes[2];  // gps | temperatura | combustible

            System.out.printf("%n[SUSCRIPTOR] %-40s %s%n", topic, json);

            switch (sensor) {
                case "gps" -> {
                    GpsData gps = new GpsData(
                        vehiculoId,
                        toDouble(datos.get("latitud")),
                        toDouble(datos.get("longitud")),
                        toDouble(datos.get("velocidad_kmh"))
                    );
                    gpsRepo.save(gps);
                    System.out.printf("[SUSCRIPTOR] GPS guardado  — %s lat=%.4f lng=%.4f%n",
                            vehiculoId, gps.getLatitud(), gps.getLongitud());
                }
                case "temperatura" -> {
                    double temp = toDouble(datos.get("temperatura_c"));
                    TempData td = new TempData(vehiculoId, temp);
                    tempRepo.save(td);

                    if (td.getAlerta()) {
                        // ── Alerta impresa en consola (requerida por la guía) ──
                        System.out.printf(
                            "⚠️  [ALERTA TEMP] %s — temperatura %.1f°C SUPERA 4°C%n",
                            vehiculoId, temp);
                        // ── Reenviar alerta a RabbitMQ vía Bridge ─────────────
                        bridge.reenviarAlertaTemperatura(vehiculoId, datos);
                    } else {
                        System.out.printf("[SUSCRIPTOR] TEMP normal  — %s %.1f°C%n",
                                vehiculoId, temp);
                    }
                }
                case "combustible" -> {
                    double nivel = toDouble(datos.get("nivel_pct"));
                    FuelData fd = new FuelData(vehiculoId, nivel);
                    fuelRepo.save(fd);

                    if (fd.getAlertaBajo()) {
                        // ── Alerta impresa en consola (requerida por la guía) ──
                        System.out.printf(
                            "⚠️  [ALERTA COMB] %s — combustible %.1f%% BAJO 20%%%n",
                            vehiculoId, nivel);
                        // ── Reenviar alerta a RabbitMQ vía Bridge ─────────────
                        bridge.reenviarAlertaCombustible(vehiculoId, datos);
                    } else {
                        System.out.printf("[SUSCRIPTOR] COMB normal  — %s %.1f%%%n",
                                vehiculoId, nivel);
                    }
                }
                default -> System.out.println("[SUSCRIPTOR] Sensor desconocido: " + sensor);
            }
        } catch (Exception e) {
            System.err.println("[SUSCRIPTOR] Error procesando mensaje: " + e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) { /* no aplica al suscriptor */ }

    // ── Utilidad ──────────────────────────────────────────────────────────────

    private static double toDouble(Object val) {
        if (val == null)            return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
