package com.guerram.DespensaFamily.dto.caja;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ArqueoResultadoDTO {
    private double efectivoEsperado;
    private double efectivoContado;
    private double diferencia;
    /** null si dio exacto: no hay nada que ajustar. */
    private MovimientoCajaDTO movimiento;
}
