package com.guerram.DespensaFamily.dto.caja;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ArqueoResultadoDTO {
    private BigDecimal efectivoEsperado;
    private BigDecimal efectivoContado;
    private BigDecimal diferencia;
    /** null si dio exacto: no hay nada que ajustar. */
    private MovimientoCajaDTO movimiento;
}
