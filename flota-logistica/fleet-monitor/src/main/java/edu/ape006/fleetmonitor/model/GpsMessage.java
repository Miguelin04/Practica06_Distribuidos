package edu.ape006.fleetmonitor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * GpsMessage — APE-006 Flota Logística
 *
 * Entidad JPA que modela un mensaje GPS recibido desde RabbitMQ.
 * Se persiste en la base de datos H2 en memoria para servir la API REST.
 *
 * Campos:
 *   vehiculoId  — identificador del vehículo (VH-001, VH-002, VH-003)
 *   latitud     — coordenada norte/sur  (rango Ecuador: -4.0 a 0.0)
 *   longitud    — coordenada este/oeste (rango Ecuador: -79.0 a -75.0)
 *   velocidadKmh— velocidad instantánea en km/h
 *   timestamp   — momento de recepción del mensaje
 */
@Entity
@Table(name = "gps_messages")
public class GpsMessage {

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

    // ── Constructor sin argumentos requerido por JPA ──────────────────────
    public GpsMessage() {}

    // ── Constructor de conveniencia ───────────────────────────────────────
    public GpsMessage(String vehiculoId, Double latitud, Double longitud, Double velocidadKmh) {
        this.vehiculoId   = vehiculoId;
        this.latitud      = latitud;
        this.longitud     = longitud;
        this.velocidadKmh = velocidadKmh;
        this.receivedAt   = LocalDateTime.now();
    }

    // ── Getters y Setters ─────────────────────────────────────────────────
    public Long getId()                       { return id; }
    public void setId(Long id)               { this.id = id; }

    public String getVehiculoId()            { return vehiculoId; }
    public void setVehiculoId(String v)      { this.vehiculoId = v; }

    public Double getLatitud()               { return latitud; }
    public void setLatitud(Double lat)       { this.latitud = lat; }

    public Double getLongitud()              { return longitud; }
    public void setLongitud(Double lng)      { this.longitud = lng; }

    public Double getVelocidadKmh()          { return velocidadKmh; }
    public void setVelocidadKmh(Double v)    { this.velocidadKmh = v; }

    public LocalDateTime getReceivedAt()     { return receivedAt; }
    public void setReceivedAt(LocalDateTime t) { this.receivedAt = t; }

    @Override
    public String toString() {
        return "GpsMessage{id=" + id +
               ", vehiculoId='" + vehiculoId + '\'' +
               ", lat=" + latitud + ", lng=" + longitud +
               ", vel=" + velocidadKmh + " km/h" +
               ", at=" + receivedAt + '}';
    }
}
