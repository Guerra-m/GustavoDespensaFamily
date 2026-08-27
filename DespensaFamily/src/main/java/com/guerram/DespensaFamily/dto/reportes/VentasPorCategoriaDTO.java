package com.guerram.DespensaFamily.dto.reportes;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VentasPorCategoriaDTO {
    private Long categoriaId;
    private String categoriaNombre;
    private Double totalVentas;
    private Double cantidadVendida;
}
