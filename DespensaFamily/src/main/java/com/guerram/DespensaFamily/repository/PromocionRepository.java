package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.Promocion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromocionRepository extends JpaRepository<Promocion, Long> {
    List<Promocion> findByActivoTrue();
    Optional<Promocion> findByProductoIdAndActivoTrue(Long productoId);
}
