package com.fleet.monitor.repository;

import com.fleet.monitor.model.TempData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * TempRepository — Spring Data JPA para la tabla temp_data.
 * Equivale a la tabla temp_data de SQLite del suscriptor Python.
 */
@Repository
public interface TempRepository extends JpaRepository<TempData, Long> {
    List<TempData> findByVehiculoIdOrderByReceivedAtDesc(String vehiculoId);
    /** Devuelve solo los registros con alerta activa (temp > 4°C). */
    List<TempData> findByAlertaTrueOrderByReceivedAtDesc();
}
