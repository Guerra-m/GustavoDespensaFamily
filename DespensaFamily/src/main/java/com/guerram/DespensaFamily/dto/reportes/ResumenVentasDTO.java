package com.guerram.DespensaFamily.dto.reportes;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResumenVentasDTO {
    private Double totalVentas;
    private Long cantidadVentas;
    private Double ganancia;
}
