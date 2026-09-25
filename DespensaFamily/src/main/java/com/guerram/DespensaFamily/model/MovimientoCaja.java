package com.guerram.DespensaFamily.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un movimiento de dinero. La unica fuente de verdad sobre cuanto entro,
 * cuanto salio y por que — cada peso que se mueve, lo haya generado una
 * venta, una compra o una persona, deja exactamente una fila aca.
 *
 * <p><b>No se edita ni se borra.</b> Un movimiento equivocado se revierte con
 * otro movimiento que lo apunta ({@link #revierteA}), nunca se cambia ni se
 * elimina la fila original. Es el mismo criterio que ya usa la anulacion de
 * ventas y compras (estado + rastro de quien y cuando), aplicado a algo que
 * es, ademas, dinero.
 *
 * <p><b>venta_id / compra_id / revierte_a son referencias sueltas y no
 * relaciones JPA</b>, a proposito: el mismo criterio que ya usa
 * {@code Venta.usuario} (un String y no un {@code @ManyToOne} a Usuario). El
 * libro de movimientos es una cosa aparte que puede señalar de donde salio un
 * peso sin depender de que esa venta o esa compra sigan existiendo tal cual.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    private TipoMovimiento tipo;

    @Enumerated(EnumType.STRING)
    private CategoriaMovimiento categoria;

    /** Siempre positivo. La direccion la da {@link #tipo}. */
    @Column(precision = 15, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private MetodoPago metodoPago;

    /** Obligatoria para GASTO, RETIRO, APORTE y AJUSTE; opcional para VENTA/COMPRA. */
    private String descripcion;

    /** Quien lo registro. Mismo criterio que Venta.usuario y Compra.usuario. */
    private String usuario;

    /** De que venta salio, si aplica. */
    @Column(name = "venta_id")
    private Long ventaId;

    /** De que compra salio, si aplica. */
    @Column(name = "compra_id")
    private Long compraId;

    /** A que empleado se le pago, si es categoria SUELDO. */
    @Column(name = "empleado_id")
    private Long empleadoId;

    /**
     * Si este movimiento revierte a otro, el id de ese otro.
     *
     * <p>{@code @Column} explicito porque la estrategia de nombres de
     * Hibernate convierte {@code revierteA} a {@code reviertea} (sin guion
     * bajo) al ser un sufijo de una sola letra mayuscula — se detecto porque
     * el arranque fallaba con "missing column [reviertea]" contra la tabla
     * que si tiene {@code revierte_a} (V4__movimiento_caja.sql).
     */
    @Column(name = "revierte_a")
    private Long revierteA;
}
