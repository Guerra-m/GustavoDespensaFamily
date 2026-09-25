package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class DetalleVenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double cantidad;
    private double precioUnitario; //precio de venta
    private double precioCosto;
    private double subtotal;

    // DF-UX-005: null cuando la linea no tuvo descuento ni promo (incluye
    // TODAS las ventas anteriores a V15). Nunca 0.0/"": eso inventaria un
    // dato que la venta original no tiene forma de reconstruir.
    private Double descuentoPorcentaje;
    private String promoDescripcion;

    @ManyToOne
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;
}
