package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.MovimientoCaja;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    /**
     * El saldo de un solo metodo de pago, HOY: es lo que separa "efectivo en el
     * cajon" de "cobrado con tarjeta o transferencia" (seccion 5 del mapa).
     *
     * <p><b>Sin filtro de fecha, a proposito.</b> Antes esto recibia un "hasta"
     * al que el servicio le pasaba {@code LocalDateTime.now()}, y ahi estaba el
     * problema (F-11 de la auditoria del 02/09/2026): cada movimiento se guarda
     * con su propia marca de tiempo, asi que si esa marca y el "ahora" de la
     * consulta no salian ordenados, el movimiento recien grabado quedaba afuera
     * del saldo. Se vio en la suite: tres corridas de ocho fallando siempre
     * igual, con una diferencia de exactamente 0.0.
     *
     * <p>El filtro no aportaba nada —el saldo de hoy es todo el libro— y podia
     * restar plata que ya estaba registrada. Para un saldo historico a una
     * fecha dada hace falta otra consulta, con su propio nombre.
     */
    @Query("""
    SELECT COALESCE(SUM(CASE WHEN m.tipo = 'INGRESO' THEN m.monto ELSE -m.monto END), 0.0)
    FROM MovimientoCaja m
    WHERE m.metodoPago = :metodo
    """)
    Double saldoActual(@Param("metodo") MetodoPago metodo);

    @Query("""
    SELECT COALESCE(SUM(m.monto), 0.0)
    FROM MovimientoCaja m
    WHERE m.tipo = 'INGRESO'
    AND m.fechaHora BETWEEN :desde AND :hasta
    """)
    Double totalIngresosEntreFechas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("""
    SELECT COALESCE(SUM(m.monto), 0.0)
    FROM MovimientoCaja m
    WHERE m.tipo = 'EGRESO'
    AND m.fechaHora BETWEEN :desde AND :hasta
    """)
    Double totalEgresosEntreFechas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /**
     * DF-UX-006 de la auditoria del 23/09: "egresos de hoy" sumaba tambien
     * las reversiones (anular una venta genera un EGRESO de categoria VENTA),
     * asi que anular una sola venta grande podia mostrar un "egresos de hoy"
     * casi todo compuesto por anulaciones, no por gastos reales. Esta consulta
     * excluye {@code revierteA IS NOT NULL} -- son solo los EGRESO originales
     * (GASTO, RETIRO, COMPRA, AJUSTE negativo), nunca una reversion.
     */
    @Query("""
    SELECT COALESCE(SUM(m.monto), 0.0)
    FROM MovimientoCaja m
    WHERE m.tipo = 'EGRESO'
    AND m.revierteA IS NULL
    AND m.fechaHora BETWEEN :desde AND :hasta
    """)
    Double totalEgresosRealesEntreFechas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Cuanto se anulo en el periodo -- reversiones de cualquier categoria, en valor absoluto (siempre positivo). */
    @Query("""
    SELECT COALESCE(SUM(m.monto), 0.0)
    FROM MovimientoCaja m
    WHERE m.revierteA IS NOT NULL
    AND m.fechaHora BETWEEN :desde AND :hasta
    """)
    Double totalAnulacionesEntreFechas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    List<MovimientoCaja> findByFechaHoraBetweenOrderByFechaHoraDesc(LocalDateTime desde, LocalDateTime hasta);

    /** Los mas recientes primero, para la pantalla de caja (seccion 6). */
    List<MovimientoCaja> findTop20ByOrderByFechaHoraDesc();

    /** Si algun movimiento ya revierte a este id — evita revertir dos veces. */
    boolean existsByRevierteA(Long revierteA);

    /**
     * El movimiento ORIGINAL (no una reversion) que genero una venta o
     * compra, para poder revertirlo al anular. revierteA IS NULL es lo que
     * distingue "el ingreso de la venta" de "la reversion de ese ingreso" —
     * sin esto, anular una venta ya anulada encontraria la reversion en vez
     * del original y la volveria a revertir, duplicando el efecto.
     */
    Optional<MovimientoCaja> findByVentaIdAndCategoriaAndRevierteAIsNull(
            Long ventaId, CategoriaMovimiento categoria);

    Optional<MovimientoCaja> findByCompraIdAndCategoriaAndRevierteAIsNull(
            Long compraId, CategoriaMovimiento categoria);

    /**
     * Lo que NETO salio de caja por una categoria en un rango. Usado por
     * Rentabilidad para "total comprado" y "total de gastos" (Fase 8).
     *
     * <p><b>Suma con signo, y ese es el punto.</b> Antes hacia
     * {@code SUM(m.monto)} sin mirar el tipo, y una reversion conserva la
     * categoria del movimiento original cambiandole solo el tipo: anular una
     * compra pagada de $5.000 dejaba "comprasTotales" en $10.000 en vez de en 0,
     * y revertir un gasto daba vuelta el signo del resultado neto (F-05 de la
     * auditoria del 02/09/2026, medido contra la aplicacion corriendo).
     *
     * <p>Sumando el EGRESO en positivo y el INGRESO en negativo, la reversion
     * cancela exactamente a su original, sin necesidad de saber cual revierte a
     * cual. Efecto de borde asumido: si el original cae en un periodo y su
     * reversion en otro, cada periodo refleja lo que paso en el — que es lo
     * mismo que ya hace el libro de caja.
     */
    @Query("""
    SELECT COALESCE(SUM(CASE WHEN m.tipo = 'EGRESO' THEN m.monto ELSE -m.monto END), 0.0)
    FROM MovimientoCaja m
    WHERE m.categoria = :categoria
    AND m.fechaHora BETWEEN :desde AND :hasta
    """)
    Double totalNetoPorCategoriaEntreFechas(
            @Param("categoria") CategoriaMovimiento categoria,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    /** Si hay algun gasto cargado en el periodo — para no mostrar un "resultado
     * neto" igual al bruto como si fuera un dato cuando en realidad nadie
     * cargo gastos (ver ReportesService.rentabilidad). */
    boolean existsByCategoriaAndFechaHoraBetween(
            CategoriaMovimiento categoria, LocalDateTime desde, LocalDateTime hasta);

    /** Los ids ya revertidos entre los que se estan mostrando, para marcarlos en el listado. */
    @Query("SELECT m.revierteA FROM MovimientoCaja m WHERE m.revierteA IN :ids")
    List<Long> idsYaRevertidosEntre(@Param("ids") List<Long> ids);
}
