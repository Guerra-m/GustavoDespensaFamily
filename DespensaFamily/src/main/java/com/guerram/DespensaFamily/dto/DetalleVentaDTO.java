package com.guerram.DespensaFamily.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleVentaDTO {
    private Long id;
    private int cantidad;
    private double precioUnitario;
    private double precioCosto;
    private double subtotal;
    private Long ventaId;
    private Long productoId;
}
