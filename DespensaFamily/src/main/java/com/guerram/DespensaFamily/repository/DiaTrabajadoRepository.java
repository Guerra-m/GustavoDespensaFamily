package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.DiaTrabajado;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaTrabajadoRepository extends JpaRepository<DiaTrabajado, Long> {

    Optional<DiaTrabajado> findByEmpleadoIdAndFecha(Long empleadoId, LocalDate fecha);

    /** Para armar la grilla semanal: todos los dias marcados en un rango, de cualquier empleado. */
    List<DiaTrabajado> findByFechaBetween(LocalDate desde, LocalDate hasta);

    long countByEmpleadoIdAndFechaBetween(Long empleadoId, LocalDate desde, LocalDate hasta);
}
