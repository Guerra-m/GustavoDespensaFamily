package com.guerram.DespensaFamily.dto.reportes;

import java.math.BigDecimal;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GananciaPorFechaDTO {
    private LocalDate fecha;
    private BigDecimal totalVentas;
    private BigDecimal ganancia;
}
