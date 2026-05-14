package edu.ape006.fleetmonitor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ape006.fleetmonitor.config.RabbitMQConfig;
import edu.ape006.fleetmonitor.model.GpsMessage;
import edu.ape006.fleetmonitor.repository.GpsMessageRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GpsConsumer — APE-006 Flota Logística
 *
 * Consumidor AMQP que escucha la cola 'cola.gps' del exchange 'exchange.fleet'.
 * Por cada mensaje GPS recibido:
 * 1. Deserializa el JSON
 * 2. Persiste la entidad GpsMessage en H2
 * 3. Imprime en consola para evidencia de la rúbrica
 */
@Component
public class GpsConsumer {

    private final GpsMessageRepository repository;
    private final ObjectMapper objectMapper;

    public GpsConsumer(GpsMessageRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Escucha la cola GPS y procesa cada mensaje.
     * El log "GPS recibido" es requerido por la rúbrica de la práctica.
     */
    @RabbitListener(queues = RabbitMQConfig.COLA_GPS)
    public void consumirGps(String mensaje) {
        try {
            // Deserializar JSON → Map para extraer campos dinámicamente
            @SuppressWarnings("unchecked")
            Map<String, Object> datos = objectMapper.readValue(mensaje, Map.class);

            String vehiculoId = (String) datos.getOrDefault("vehiculo_id", "DESCONOCIDO");
            Double latitud = toDouble(datos.get("latitud"));
            Double longitud = toDouble(datos.get("longitud"));
            Double velocidad = toDouble(datos.get("velocidad_kmh"));

            // Persistir en H2
            GpsMessage gps = new GpsMessage(vehiculoId, latitud, longitud, velocidad);
            repository.save(gps);

            // ── Log requerido por la rúbrica ──────────────────────────────
            System.out.printf(
                    "[GPS CONSUMER] GPS recibido — %s | lat=%.4f | lng=%.4f | vel=%.1f km/h%n",
                    vehiculoId, latitud, longitud, velocidad);

        } catch (Exception e) {
            System.err.println("[GPS CONSUMER] Error procesando mensaje: " + e.getMessage());
            System.err.println("               Mensaje raw: " + mensaje);
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
