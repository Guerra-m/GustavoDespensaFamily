package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoMasVendidoDTO;
import com.guerram.DespensaFamily.model.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {

    @Query("""
    SELECT COALESCE(SUM(dv.cantidad * dv.precioCosto), 0)
    FROM DetalleVenta dv
    JOIN dv.venta v
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND venta.estado = 'ACTIVA'
""")
    Double totalCostoEntreFechas(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );


    @Query("""
    SELECT new com.guerram.DespensaFamily.dto.reportes.ProductoMasVendidoDTO(
        p.id,
        p.nombre,
        SUM(dv.cantidad)
    )
    FROM DetalleVenta dv
    JOIN dv.producto p
    JOIN dv.venta v
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND venta.estado = 'ACTIVA'
    GROUP BY p.id, p.nombre
    ORDER BY SUM(dv.cantidad) DESC
""")
    List<ProductoMasVendidoDTO> productosMasVendidos(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );

    @Query("""
    SELECT new com.guerram.DespensaFamily.dto.reportes.MovimientoDTO(
        v.fechaHora,
        'VENTA',
        p.id,
        p.nombre,
        dv.cantidad,
        dv.precioUnitario,
        dv.subtotal,
        v.id
    )
    FROM DetalleVenta dv
    JOIN dv.venta v
    JOIN dv.producto p
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND venta.estado = 'ACTIVA'
""")
    List<MovimientoDTO> movimientosVentas(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );

}
