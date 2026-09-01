package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.dto.reportes.GananciaPorFechaDTO;
import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoMasVendidoDTO;
import com.guerram.DespensaFamily.dto.reportes.VentasPorCategoriaDTO;
import com.guerram.DespensaFamily.model.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {

    @Query("""
    SELECT COALESCE(SUM(dv.cantidad * dv.precioCosto), 0.0)
    FROM DetalleVenta dv
    JOIN dv.venta v
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND v.estado = 'ACTIVA'
    """)
    Double totalCostoEntreFechas(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
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
    AND v.estado = 'ACTIVA'
    GROUP BY p.id, p.nombre
    ORDER BY SUM(dv.cantidad) DESC
    """)
    List<ProductoMasVendidoDTO> productosMasVendidos(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
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
    AND v.estado = 'ACTIVA'
    """)
    List<MovimientoDTO> movimientosVentas(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

    @Query("""
    SELECT new com.guerram.DespensaFamily.dto.reportes.VentasPorCategoriaDTO(
        c.id,
        COALESCE(c.nombre, 'Sin categoría'),
        SUM(dv.subtotal),
        SUM(dv.cantidad)
    )
    FROM DetalleVenta dv
    JOIN dv.venta v
    JOIN dv.producto p
    LEFT JOIN p.categoria c
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND v.estado = 'ACTIVA'
    AND p.activo = true
    GROUP BY c.id, c.nombre
    ORDER BY SUM(dv.subtotal) DESC
    """)
    List<VentasPorCategoriaDTO> ventasPorCategoria(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

    @Query("""
    SELECT new com.guerram.DespensaFamily.dto.reportes.GananciaPorFechaDTO(
        CAST(v.fechaHora AS LocalDate),
        SUM(dv.subtotal),
        SUM(dv.cantidad * (dv.precioUnitario - dv.precioCosto))
    )
    FROM DetalleVenta dv
    JOIN dv.venta v
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND v.estado = 'ACTIVA'
    GROUP BY CAST(v.fechaHora AS LocalDate)
    ORDER BY CAST(v.fechaHora AS LocalDate)
    """)
    List<GananciaPorFechaDTO> gananciaPorFecha(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );
}