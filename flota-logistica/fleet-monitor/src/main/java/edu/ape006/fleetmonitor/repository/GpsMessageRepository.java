package edu.ape006.fleetmonitor.repository;

import edu.ape006.fleetmonitor.model.GpsMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * GpsMessageRepository — APE-006 Flota Logística
 *
 * Repositorio Spring Data JPA para persistir y consultar
 * los mensajes GPS recibidos desde RabbitMQ.
 */
@Repository
public interface GpsMessageRepository extends JpaRepository<GpsMessage, Long> {

    /** Devuelve todos los mensajes de un vehículo específico. */
    List<GpsMessage> findByVehiculoIdOrderByReceivedAtDesc(String vehiculoId);
}
