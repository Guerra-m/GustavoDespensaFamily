package com.guerram.DespensaFamily.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    // DF-SEC-003: null (y fuera del JSON) para quien no ve costos — ver
    // ConfiguracionController.obtener.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double gananciaPorDefecto;
}
