package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class DetalleVenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 15, scale = 3)
    private BigDecimal cantidad;

    @Column(precision = 15, scale = 2)
    private BigDecimal precioUnitario; //precio de venta

    @Column(precision = 15, scale = 2)
    private BigDecimal precioCosto;

    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    // DF-UX-005: null cuando la linea no tuvo descuento ni promo (incluye
    // TODAS las ventas anteriores a V15). Nunca 0.0/"": eso inventaria un
    // dato que la venta original no tiene forma de reconstruir.
    @Column(precision = 5, scale = 2)
    private BigDecimal descuentoPorcentaje;
    private String promoDescripcion;

    @ManyToOne
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;
}
