package com.fleet.monitor.controller;

import com.fleet.monitor.model.FuelData;
import com.fleet.monitor.model.GpsData;
import com.fleet.monitor.model.TempData;
import com.fleet.monitor.repository.FuelRepository;
import com.fleet.monitor.repository.GpsRepository;
import com.fleet.monitor.repository.TempRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * FleetController — APE-06 Flota Logística
 *
 * API REST del sistema de monitoreo de flota.
 * Endpoints disponibles:
 *
 *   GET /api/fleet/status              — estado general de todos los vehículos
 *   GET /api/fleet/vehicles            — lista de vehículos activos
 *   GET /api/fleet/vehicle/{id}/telemetry — última telemetría de un vehículo
 *   GET /api/fleet/alertas             — todas las alertas activas (temp + comb)
 *   GET /api/fleet/health              — health-check del servicio
 */
@RestController
@RequestMapping("/api/fleet")
@CrossOrigin(origins = "*")
public class FleetController {

    private final GpsRepository  gpsRepo;
    private final TempRepository tempRepo;
    private final FuelRepository fuelRepo;

    public FleetController(GpsRepository gpsRepo, TempRepository tempRepo, FuelRepository fuelRepo) {
        this.gpsRepo  = gpsRepo;
        this.tempRepo = tempRepo;
        this.fuelRepo = fuelRepo;
    }

    // ── GET /api/fleet/status ─────────────────────────────────────────────────
    /**
     * Estado general de la flota:
     * - Total de registros por tipo de sensor
     * - Vehículos activos (únicos)
     * - Última posición conocida por vehículo
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getFleetStatus() {
        List<GpsData>  todosGps  = gpsRepo.findAll();
        List<TempData> todosTemp = tempRepo.findAll();
        List<FuelData> todosFuel = fuelRepo.findAll();

        // Última posición por vehículo (sobreescribe → queda el último)
        Map<String, GpsData> ultimaPosicion = new LinkedHashMap<>();
        for (GpsData gps : todosGps) {
            ultimaPosicion.put(gps.getVehiculoId(), gps);
        }

        // Alertas activas
        long alertasTemp = todosTemp.stream().filter(t -> Boolean.TRUE.equals(t.getAlerta())).count();
        long alertasFuel = todosFuel.stream().filter(f -> Boolean.TRUE.equals(f.getAlertaBajo())).count();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("servicio",           "Fleet Monitor — APE-05/06");
        status.put("timestamp",          LocalDateTime.now().toString());
        status.put("vehiculos_activos",  ultimaPosicion.size());
        status.put("total_gps",          todosGps.size());
        status.put("total_temp",         todosTemp.size());
        status.put("total_fuel",         todosFuel.size());
        status.put("alertas_temp_activas", alertasTemp);
        status.put("alertas_fuel_activas", alertasFuel);
        status.put("ultima_posicion",    ultimaPosicion.values());

        return ResponseEntity.ok(status);
    }

    // ── GET /api/fleet/vehicles ───────────────────────────────────────────────
    /**
     * Lista de vehículos únicos que han enviado telemetría.
     */
    @GetMapping("/vehicles")
    public ResponseEntity<Map<String, Object>> getVehicles() {
        List<GpsData> todos = gpsRepo.findAll();
        Set<String> vehiculos = new LinkedHashSet<>();
        todos.forEach(g -> vehiculos.add(g.getVehiculoId()));
        return ResponseEntity.ok(Map.of(
            "vehiculos",  vehiculos,
            "total",      vehiculos.size(),
            "timestamp",  LocalDateTime.now().toString()
        ));
    }

    // ── GET /api/fleet/vehicle/{id}/telemetry ─────────────────────────────────
    /**
     * Última telemetría completa (GPS + temp + combustible) de un vehículo.
     * Ejemplo: GET /api/fleet/vehicle/VH-001/telemetry
     */
    @GetMapping("/vehicle/{id}/telemetry")
    public ResponseEntity<Map<String, Object>> getTelemetry(@PathVariable String id) {
        List<GpsData>  gps  = gpsRepo.findByVehiculoIdOrderByReceivedAtDesc(id);
        List<TempData> temp = tempRepo.findByVehiculoIdOrderByReceivedAtDesc(id);
        List<FuelData> fuel = fuelRepo.findByVehiculoIdOrderByReceivedAtDesc(id);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("vehiculo_id",          id);
        resp.put("timestamp",            LocalDateTime.now().toString());
        resp.put("ultimo_gps",           gps.isEmpty()  ? null : gps.get(0));
        resp.put("ultima_temperatura",   temp.isEmpty() ? null : temp.get(0));
        resp.put("ultimo_combustible",   fuel.isEmpty() ? null : fuel.get(0));
        resp.put("historial_gps_count",  gps.size());
        resp.put("historial_temp_count", temp.size());
        resp.put("historial_fuel_count", fuel.size());

        return ResponseEntity.ok(resp);
    }

    // ── GET /api/fleet/alertas ────────────────────────────────────────────────
    /**
     * Todas las alertas activas de temperatura (> 4°C) y combustible (< 20%).
     */
    @GetMapping("/alertas")
    public ResponseEntity<Map<String, Object>> getAlertas() {
        List<TempData> alertasTemp = tempRepo.findByAlertaTrueOrderByReceivedAtDesc();
        List<FuelData> alertasFuel = fuelRepo.findByAlertaBajoTrueOrderByReceivedAtDesc();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("timestamp",          LocalDateTime.now().toString());
        resp.put("alertas_temperatura", alertasTemp);
        resp.put("alertas_combustible", alertasFuel);
        resp.put("total_alertas_temp", alertasTemp.size());
        resp.put("total_alertas_fuel", alertasFuel.size());

        return ResponseEntity.ok(resp);
    }

    // ── GET /api/fleet/health ─────────────────────────────────────────────────
    /** Health-check rápido del servicio. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status",    "UP",
            "service",   "fleet-monitor",
            "timestamp", LocalDateTime.now().toString()
        ));
    }
}
