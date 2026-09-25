package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.EstadoVenta;
import com.guerram.DespensaFamily.model.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    /** DF-BUG-003: la venta ya creada con esta Idempotency-Key, si existe. */
    Optional<Venta> findByClaveIdempotencia(String claveIdempotencia);

    @Query("""
    SELECT COALESCE(SUM(v.total), 0.0)
    FROM Venta v
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND v.estado = 'ACTIVA'
    """)
    Double totalVentasEntreFechas(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

    @Query("""
    SELECT COUNT(v)
    FROM Venta v
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND v.estado = 'ACTIVA'
    """)
    Long cantidadVentasEntreFechas(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

    List<Venta> findByEstado(EstadoVenta estado);

    // DF-PERF-001: desde/hasta opcionales -- el COALESCE con el propio
    // parametro es un truco de H2/JPQL para "si es null, no filtres por esto"
    // sin tener que armar dos queries a mano. Orden mas nuevo primero: es lo
    // que espera ver quien abre el historial.
    @Query("""
    SELECT v FROM Venta v
    WHERE (:desde IS NULL OR v.fechaHora >= :desde)
    AND (:hasta IS NULL OR v.fechaHora <= :hasta)
    ORDER BY v.fechaHora DESC
    """)
    Page<Venta> buscarPaginado(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);
}