package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    List<Producto> findByCodigoBarrasContaining(String codigoBarras);

    List<Producto> findByCategoria_Id(Long categoriaId);

    List<Producto> findByNombreContainingIgnoreCaseAndCodigoBarrasContainingAndCategoria_Id(
            String nombre,
            String codigoBarras,
            Long categoriaId
    );
    List<Producto> findByStockLessThan(Integer limite);
    List<Producto> findByActivoTrue();
    List<Producto> findByStockLessThanAndActivoTrue(Integer stock);
}
