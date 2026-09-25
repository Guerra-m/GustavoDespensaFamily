package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class DetalleCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 15, scale = 3)
    private BigDecimal cantidad;

    @Column(precision = 15, scale = 2)
    private BigDecimal precioCosto;

    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    // Que costo tenia el producto ANTES de esta compra. Sin guardarlo, anular
    // podia devolver el stock pero no el precio: una compra con el costo mal
    // tipeado dejaba el producto con ese valor para siempre.
    @Column(precision = 15, scale = 2)
    private BigDecimal precioCostoAnterior;

    @ManyToOne
    @JoinColumn(name = "compra_id")
    private Compra compra;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;
}
