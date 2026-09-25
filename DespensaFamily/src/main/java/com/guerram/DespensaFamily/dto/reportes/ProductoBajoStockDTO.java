package com.guerram.DespensaFamily.dto.reportes;

import java.math.BigDecimal;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductoBajoStockDTO {
    private Long id;
    private String nombre;
    private BigDecimal stock;
}
