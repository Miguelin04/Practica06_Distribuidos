package com.fleet.monitor.repository;

import com.fleet.monitor.model.GpsData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * GpsRepository — Spring Data JPA para la tabla gps_data.
 * Equivale a la tabla gps_data de SQLite del suscriptor Python.
 */
@Repository
public interface GpsRepository extends JpaRepository<GpsData, Long> {
    List<GpsData> findByVehiculoIdOrderByReceivedAtDesc(String vehiculoId);
}
