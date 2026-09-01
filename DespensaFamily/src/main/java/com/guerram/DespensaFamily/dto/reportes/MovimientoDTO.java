package com.guerram.DespensaFamily.dto.reportes;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MovimientoDTO {

    private LocalDateTime fecha;
    private String tipo; // "COMPRA" o "VENTA"

    private Long productoId;
    private String productoNombre;

    private double cantidad;
    private Double precio;
    private Double subtotal;

    private Long operacionId; // compraId o ventaId
}
