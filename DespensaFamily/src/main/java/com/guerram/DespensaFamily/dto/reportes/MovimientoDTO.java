package com.guerram.DespensaFamily.dto.reportes;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MovimientoDTO {

    private LocalDate fecha;
    private String tipo; // "COMPRA" o "VENTA"

    private Long productoId;
    private String productoNombre;

    private Integer cantidad;
    private Double precio;
    private Double subtotal;

    private Long operacionId; // compraId o ventaId
}
