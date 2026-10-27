package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.Descuento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DescuentoRepository extends JpaRepository<Descuento, Long> {
    List<Descuento> findByActivoTrue();
    Optional<Descuento> findByProductoIdAndActivoTrue(Long productoId);
    Optional<Descuento> findByCategoriaIdAndActivoTrue(Long categoriaId);
}
