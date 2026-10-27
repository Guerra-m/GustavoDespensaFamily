package com.guerram.DespensaFamily.dto.reportes;

import java.math.BigDecimal;

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

    private BigDecimal cantidad;
    private BigDecimal precio;
    private BigDecimal subtotal;

    private Long operacionId; // compraId o ventaId
}
