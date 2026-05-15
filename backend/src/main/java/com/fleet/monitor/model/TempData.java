package com.fleet.monitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * TempData — APE-05 Flota Logística
 *
 * Entidad JPA que modela un registro de temperatura recibido desde el topic
 * MQTT flota/{vehiculoId}/temperatura, persistido en la tabla temp_data de H2.
 *
 * Equivale a la tabla 'temp_data' del suscriptor SQLite en Python.
 *
 * Rango operativo:  -5 °C a 8 °C (cadena de frío logística)
 * Umbral de alerta: temperatura > 4 °C
 */
@Entity
@Table(name = "temp_data")
public class TempData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehiculo_id", nullable = false)
    private String vehiculoId;

    /** Temperatura de la carga en grados Celsius. */
    @Column(name = "temperatura_c")
    private Double temperaturaC;

    /** true si temperatura > 4 °C (alerta de rotura de cadena de frío). */
    private Boolean alerta;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    // ── JPA requiere constructor sin argumentos ────────────────────────────────
    public TempData() {}

    public TempData(String vehiculoId, Double temperaturaC) {
        this.vehiculoId    = vehiculoId;
        this.temperaturaC  = temperaturaC;
        this.alerta        = temperaturaC > 4.0;   // umbral de la guía APE-05
        this.receivedAt    = LocalDateTime.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }

    public String getVehiculoId()               { return vehiculoId; }
    public void setVehiculoId(String v)         { this.vehiculoId = v; }

    public Double getTemperaturaC()             { return temperaturaC; }
    public void setTemperaturaC(Double t)       { this.temperaturaC = t; }

    public Boolean getAlerta()                  { return alerta; }
    public void setAlerta(Boolean a)            { this.alerta = a; }

    public LocalDateTime getReceivedAt()        { return receivedAt; }
    public void setReceivedAt(LocalDateTime t)  { this.receivedAt = t; }

    @Override
    public String toString() {
        return "TempData{id=" + id + ", vehiculoId='" + vehiculoId + '\'' +
               ", temp=" + temperaturaC + "°C, alerta=" + alerta + ", at=" + receivedAt + '}';
    }
}
