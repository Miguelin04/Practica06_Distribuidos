package edu.ape006.fleetmonitor.controller;

import edu.ape006.fleetmonitor.model.GpsMessage;
import edu.ape006.fleetmonitor.repository.GpsMessageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * FleetController — APE-006 Flota Logística
 *
 * Expone la API REST para consultar el estado de la flota.
 * Endpoints:
 *   GET /api/fleet/status          — estado general de todos los vehículos
 *   GET /api/fleet/gps             — todos los mensajes GPS almacenados
 *   GET /api/fleet/gps/{vehiculoId}— mensajes GPS de un vehículo específico
 *   GET /api/fleet/health          — health-check del servicio
 */
@RestController
@RequestMapping("/api/fleet")
@CrossOrigin(origins = "*")
public class FleetController {

    private final GpsMessageRepository gpsRepository;

    public FleetController(GpsMessageRepository gpsRepository) {
        this.gpsRepository = gpsRepository;
    }

    // ── GET /api/fleet/status ──────────────────────────────────────────────
    /**
     * Devuelve el estado general de la flota:
     * - Total de mensajes GPS recibidos
     * - Lista de vehículos activos (únicos)
     * - Última posición conocida de cada vehículo
     * - Timestamp del servidor
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getFleetStatus() {
        List<GpsMessage> todos = gpsRepository.findAll();

        // Última posición por vehículo
        Map<String, GpsMessage> ultimaPosicion = new LinkedHashMap<>();
        for (GpsMessage gps : todos) {
            ultimaPosicion.put(gps.getVehiculoId(), gps);  // sobreescribe → queda el último
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("servicio",         "Fleet Monitor — APE-006");
        status.put("timestamp",        LocalDateTime.now().toString());
        status.put("total_gps_recibidos", todos.size());
        status.put("vehiculos_activos",  ultimaPosicion.size());
        status.put("ultima_posicion",    ultimaPosicion.values());

        return ResponseEntity.ok(status);
    }

    // ── GET /api/fleet/gps ────────────────────────────────────────────────
    /**
     * Devuelve todos los mensajes GPS persistidos (máximo 100 registros).
     */
    @GetMapping("/gps")
    public ResponseEntity<List<GpsMessage>> getAllGps() {
        List<GpsMessage> mensajes = gpsRepository.findAll();
        // Limitar a los últimos 100 para no saturar la respuesta
        int desde = Math.max(0, mensajes.size() - 100);
        return ResponseEntity.ok(mensajes.subList(desde, mensajes.size()));
    }

    // ── GET /api/fleet/gps/{vehiculoId} ───────────────────────────────────
    /**
     * Devuelve los mensajes GPS de un vehículo específico.
     * Ejemplo: GET /api/fleet/gps/VH-001
     */
    @GetMapping("/gps/{vehiculoId}")
    public ResponseEntity<List<GpsMessage>> getGpsByVehiculo(@PathVariable String vehiculoId) {
        List<GpsMessage> mensajes =
            gpsRepository.findByVehiculoIdOrderByReceivedAtDesc(vehiculoId);
        return ResponseEntity.ok(mensajes);
    }

    // ── GET /api/fleet/health ─────────────────────────────────────────────
    /**
     * Health-check simple para verificar que el servicio está activo.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "fleet-monitor",
            "timestamp", LocalDateTime.now().toString()
        ));
    }
}
