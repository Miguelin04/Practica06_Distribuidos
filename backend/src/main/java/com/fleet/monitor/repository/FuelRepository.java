package com.fleet.monitor.repository;

import com.fleet.monitor.model.FuelData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * FuelRepository — Spring Data JPA para la tabla fuel_data.
 * Equivale a la tabla fuel_data de SQLite del suscriptor Python.
 */
@Repository
public interface FuelRepository extends JpaRepository<FuelData, Long> {
    List<FuelData> findByVehiculoIdOrderByReceivedAtDesc(String vehiculoId);
    /** Devuelve solo los registros con alerta activa (nivel < 20%). */
    List<FuelData> findByAlertaBajoTrueOrderByReceivedAtDesc();
}
