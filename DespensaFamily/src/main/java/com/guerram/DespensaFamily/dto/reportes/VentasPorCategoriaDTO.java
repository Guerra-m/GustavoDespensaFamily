package com.guerram.DespensaFamily.dto.reportes;

import java.math.BigDecimal;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VentasPorCategoriaDTO {
    private Long categoriaId;
    private String categoriaNombre;
    private BigDecimal totalVentas;
    private BigDecimal cantidadVendida;
}
