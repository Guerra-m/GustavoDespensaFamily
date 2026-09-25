package com.guerram.DespensaFamily.dto.reportes;

import java.math.BigDecimal;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductoMasVendidoDTO {
    private Long productoId;
    private String nombre;
    private BigDecimal cantidadVendida;
}
