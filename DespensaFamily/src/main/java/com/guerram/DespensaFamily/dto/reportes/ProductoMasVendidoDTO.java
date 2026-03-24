package com.guerram.DespensaFamily.dto.reportes;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductoMasVendidoDTO {
    private Long productoId;
    private String nombre;
    private Long cantidadVendida;
}
