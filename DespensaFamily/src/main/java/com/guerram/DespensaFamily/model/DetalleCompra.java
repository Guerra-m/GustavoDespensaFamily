package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class DetalleCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double cantidad;
    private double precioCosto;
    private double subtotal;

    // Que costo tenia el producto ANTES de esta compra. Sin guardarlo, anular
    // podia devolver el stock pero no el precio: una compra con el costo mal
    // tipeado dejaba el producto con ese valor para siempre.
    private Double precioCostoAnterior;

    @ManyToOne
    @JoinColumn(name = "compra_id")
    private Compra compra;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;
}
