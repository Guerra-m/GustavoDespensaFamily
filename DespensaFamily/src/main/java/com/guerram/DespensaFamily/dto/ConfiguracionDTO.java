package com.guerram.DespensaFamily.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionDTO {
    private Double umbralStockBajo;
    private String nombreNegocio;
    private Double gananciaPorDefecto;
}
