package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.model.DetalleCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DetalleCompraRepository extends JpaRepository<DetalleCompra,Long> {

    @Query("""
    SELECT new com.guerram.DespensaFamily.dto.reportes.MovimientoDTO(
        c.fechaHora,
        'COMPRA',
        p.id,
        p.nombre,
        dc.cantidad,
        dc.precioCosto,
        dc.subtotal,
        c.id
    )
    FROM DetalleCompra dc
    JOIN dc.compra c
    JOIN dc.producto p
    WHERE c.fechaHora BETWEEN :desde AND :hasta
""")
    List<MovimientoDTO> movimientosCompras(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );

}
