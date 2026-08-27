package com.guerram.DespensaFamily.dto.reportes;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GananciaPorFechaDTO {
    private LocalDate fecha;
    private Double totalVentas;
    private Double ganancia;
}
