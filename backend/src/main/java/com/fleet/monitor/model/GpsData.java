package com.fleet.monitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * GpsData — APE-05 Flota Logística
 *
 * Entidad JPA que modela un registro GPS recibido desde el topic MQTT
 * flota/{vehiculoId}/gps y persistido en la tabla gps_data de H2.
 *
 * Equivale a la tabla 'gps_data' que en la versión Python se guardaba en SQLite.
 *
 * Coordenadas base: Guayaquil lat=-2.1709, lng=-79.9224
 */
@Entity
@Table(name = "gps_data")
public class GpsData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehiculo_id", nullable = false)
    private String vehiculoId;

    private Double latitud;
    private Double longitud;
    private Double velocidadKmh;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    // ── JPA requiere constructor sin argumentos ────────────────────────────────
    public GpsData() {}

    public GpsData(String vehiculoId, Double latitud, Double longitud, Double velocidadKmh) {
        this.vehiculoId    = vehiculoId;
        this.latitud       = latitud;
        this.longitud      = longitud;
        this.velocidadKmh  = velocidadKmh;
        this.receivedAt    = LocalDateTime.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }

    public String getVehiculoId()               { return vehiculoId; }
    public void setVehiculoId(String v)         { this.vehiculoId = v; }

    public Double getLatitud()                  { return latitud; }
    public void setLatitud(Double lat)          { this.latitud = lat; }

    public Double getLongitud()                 { return longitud; }
    public void setLongitud(Double lng)         { this.longitud = lng; }

    public Double getVelocidadKmh()             { return velocidadKmh; }
    public void setVelocidadKmh(Double v)       { this.velocidadKmh = v; }

    public LocalDateTime getReceivedAt()        { return receivedAt; }
    public void setReceivedAt(LocalDateTime t)  { this.receivedAt = t; }

    @Override
    public String toString() {
        return "GpsData{id=" + id + ", vehiculoId='" + vehiculoId + '\'' +
               ", lat=" + latitud + ", lng=" + longitud +
               ", vel=" + velocidadKmh + " km/h, at=" + receivedAt + '}';
    }
}
