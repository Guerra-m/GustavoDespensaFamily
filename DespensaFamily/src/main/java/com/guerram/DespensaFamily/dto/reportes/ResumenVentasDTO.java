package com.guerram.DespensaFamily.dto.reportes;

import java.math.BigDecimal;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResumenVentasDTO {
    private BigDecimal totalVentas;
    private Long cantidadVentas;
    private BigDecimal ganancia;
}
