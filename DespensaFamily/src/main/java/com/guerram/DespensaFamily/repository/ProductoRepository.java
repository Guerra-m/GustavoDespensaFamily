package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    List<Producto> findByActivoTrue();
    List<Producto> findByStockLessThanAndActivoTrue(Double stock);

    /**
     * Trae el producto con el registro bloqueado para escritura.
     *
     * <p>Vender y comprar hacen leer-stock / restar / escribir-stock. Con solo
     * {@code @Version} eso se resuelve a los choques: dos cajas vendiendo el
     * mismo producto chocan, reintentan, vuelven a chocar. Medido con 20 ventas
     * simultaneas, el reintento se agotaba de a ratos y una venta terminaba en
     * error real para el cajero (el test fallaba 1 de cada 5 corridas).
     *
     * <p>Con el bloqueo, el segundo hilo espera en la base en vez de competir.
     * {@code @Version} se deja igual: sigue cubriendo las ediciones de producto
     * desde la pantalla de Productos, que no pasan por aca.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> buscarParaActualizarStock(@Param("id") Long id);
}
