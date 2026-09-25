package com.guerram.DespensaFamily.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleVentaDTO {
    private Long id;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;

    // DF-SEC-003: null para quien no ve costos (ver PermisosDeLectura), y
    // en ese caso el campo no viaja en el JSON. Double y no double por eso.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal precioCosto;

    private BigDecimal subtotal;
    private BigDecimal descuentoPorcentaje;
    private String promoDescripcion;
    private Long ventaId;
    private Long productoId;
}
