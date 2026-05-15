package com.fleet.monitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * FuelData — APE-05 Flota Logística
 *
 * Entidad JPA que modela un registro de combustible recibido desde el topic
 * MQTT flota/{vehiculoId}/combustible, persistido en la tabla fuel_data de H2.
 *
 * Equivale a la tabla 'fuel_data' del suscriptor SQLite en Python.
 *
 * Umbral de alerta: nivel < 20 % (guía APE-05)
 */
@Entity
@Table(name = "fuel_data")
public class FuelData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehiculo_id", nullable = false)
    private String vehiculoId;

    /** Nivel de combustible en porcentaje (0–100). */
    @Column(name = "nivel_pct")
    private Double nivelPct;

    /** true si nivelPct < 20 % (alerta de combustible bajo). */
    @Column(name = "alerta_bajo")
    private Boolean alertaBajo;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    // ── JPA requiere constructor sin argumentos ────────────────────────────────
    public FuelData() {}

    public FuelData(String vehiculoId, Double nivelPct) {
        this.vehiculoId  = vehiculoId;
        this.nivelPct    = nivelPct;
        this.alertaBajo  = nivelPct < 20.0;   // umbral de la guía APE-05
        this.receivedAt  = LocalDateTime.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }

    public String getVehiculoId()               { return vehiculoId; }
    public void setVehiculoId(String v)         { this.vehiculoId = v; }

    public Double getNivelPct()                 { return nivelPct; }
    public void setNivelPct(Double n)           { this.nivelPct = n; }

    public Boolean getAlertaBajo()              { return alertaBajo; }
    public void setAlertaBajo(Boolean a)        { this.alertaBajo = a; }

    public LocalDateTime getReceivedAt()        { return receivedAt; }
    public void setReceivedAt(LocalDateTime t)  { this.receivedAt = t; }

    @Override
    public String toString() {
        return "FuelData{id=" + id + ", vehiculoId='" + vehiculoId + '\'' +
               ", nivel=" + nivelPct + "%, alertaBajo=" + alertaBajo + ", at=" + receivedAt + '}';
    }
}
