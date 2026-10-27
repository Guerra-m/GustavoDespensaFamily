package com.guerram.DespensaFamily.dto;

import java.math.BigDecimal;

import java.util.List;
import lombok.*;

/** Respuesta de POST /ventas/cotizar — ver VentaService.cotizar. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CotizacionDTO {
    private List<LineaCotizadaDTO> lineas;
    private BigDecimal total;
}
