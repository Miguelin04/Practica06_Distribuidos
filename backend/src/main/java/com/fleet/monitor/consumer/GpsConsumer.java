package com.fleet.monitor.consumer;

import com.fleet.monitor.config.RabbitMQConfig;
import com.fleet.monitor.model.GpsData;
import com.fleet.monitor.repository.GpsRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GpsConsumer — APE-06 Flota Logística
 *
 * Consumidor AMQP que escucha la cola 'cola.gps' de RabbitMQ.
 * Por cada mensaje GPS recibido:
 *   1. Extrae vehiculoId, latitud, longitud y velocidad
 *   2. Persiste GpsData en H2 (tabla gps_data)
 *   3. Imprime log "[GPS CONSUMER] GPS recibido" (requerido por la rúbrica)
 *
 * El mensaje llega como Map<String,Object> gracias al Jackson2JsonMessageConverter
 * configurado en RabbitMQConfig.
 */
@Component
public class GpsConsumer {

    private final GpsRepository gpsRepo;

    public GpsConsumer(GpsRepository gpsRepo) {
        this.gpsRepo = gpsRepo;
    }

    /** Escucha la cola GPS y persiste cada mensaje en H2. */
    @RabbitListener(queues = RabbitMQConfig.COLA_GPS)
    public void consumirGps(Map<String, Object> datos) {
        try {
            String vehiculoId = (String) datos.getOrDefault("vehiculo_id", "DESCONOCIDO");
            double latitud    = toDouble(datos.get("latitud"));
            double longitud   = toDouble(datos.get("longitud"));
            double velocidad  = toDouble(datos.get("velocidad_kmh"));

            GpsData gps = new GpsData(vehiculoId, latitud, longitud, velocidad);
            gpsRepo.save(gps);

            // ── Log requerido por la rúbrica ──────────────────────────────────
            System.out.printf(
                "[GPS CONSUMER] GPS recibido — %s | lat=%.4f | lng=%.4f | vel=%.1f km/h%n",
                vehiculoId, latitud, longitud, velocidad);

        } catch (Exception e) {
            System.err.println("[GPS CONSUMER] Error procesando mensaje: " + e.getMessage());
        }
    }

    private static double toDouble(Object val) {
        if (val == null)             return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
