package com.guerram.DespensaFamily.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleVentaDTO {
    private Long id;
    private double cantidad;
    private double precioUnitario;
    private double precioCosto;
    private double subtotal;
    private Double descuentoPorcentaje;
    private String promoDescripcion;
    private Long ventaId;
    private Long productoId;
}
