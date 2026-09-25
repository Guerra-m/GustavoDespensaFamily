package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.dto.reportes.GananciaPorFechaDTO;
import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoMasVendidoDTO;
import com.guerram.DespensaFamily.dto.reportes.VentasPorCategoriaDTO;
import com.guerram.DespensaFamily.model.DetalleVenta;
import org.springframework.data.domain.Pageable;
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

    // DF-PERF-002 de la auditoria del 23/09: antes esta consulta devolvia
    // TODOS los productos vendidos alguna vez en el rango (166 KB medidos
    // contra la base real) y ReportesService recortaba en Java. Ahora
    // filtra p.activo=true en el SQL (nada de findById por fila) y el
    // Pageable limita el resultado a un top acotado sin traer de mas.
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
    AND p.activo = true
    GROUP BY p.id, p.nombre
    ORDER BY SUM(dv.cantidad) DESC
    """)
    List<ProductoMasVendidoDTO> productosMasVendidos(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable
    );

    // DF-PERF-002: "AND p.activo = true" reemplaza el filtro que
    // ReportesService.movimientosPorFecha hacia con un findById por fila
    // (N+1 medido en 1,09 s para 2 meses de datos reales).
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
    AND p.activo = true
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

    // La ganancia se calcula sobre lo que REALMENTE se cobro (subtotal), no
    // sobre cantidad * precioUnitario. Con una promocion activa (ej. 3x2) el
    // subtotal ya cobra menos unidades de las que salieron por precio de lista,
    // y cantidad * (precioUnitario - precioCosto) ignora eso por completo:
    // con un 3x2 de $1.000 y costo $600 sobre 3 unidades, la ganancia real es
    // $200 (se cobraron 2, costaron 3) y la formula vieja daba $1.200, seis
    // veces mas. subtotal - cantidad*precioCosto da el numero correcto sin
    // necesitar saber si hubo promocion o no.
    @Query("""
    SELECT new com.guerram.DespensaFamily.dto.reportes.GananciaPorFechaDTO(
        CAST(v.fechaHora AS LocalDate),
        SUM(dv.subtotal),
        SUM(dv.subtotal - dv.cantidad * dv.precioCosto)
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