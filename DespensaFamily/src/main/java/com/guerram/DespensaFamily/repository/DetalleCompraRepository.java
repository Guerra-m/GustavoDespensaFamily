package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.model.DetalleCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DetalleCompraRepository extends JpaRepository<DetalleCompra, Long> {

    // AND c.estado = 'ACTIVA': una compra anulada ya devolvio el stock y
    // restauro el precio anterior (ver CompraService.anularCompra), asi que no
    // deberia figurar como si hubiera pasado. Antes de este filtro,
    // movimientosVentas si filtraba por estado y movimientosCompras no —
    // asimetria que dejaba compras anuladas contando en el listado.
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
    AND c.estado = 'ACTIVA'
    """)
    List<MovimientoDTO> movimientosCompras(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );
}