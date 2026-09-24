package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.Empleado;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {
    Optional<Empleado> findByUsername(String username);
}
