package com.guerram.DespensaFamily.dto.caja;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Contar el cajón y registrar la diferencia contra lo que la caja esperaba (Fase 6). */
@Getter
@Setter
@NoArgsConstructor
public class ArqueoRequestDTO {

    @NotNull(message = "Hay que indicar cuánto efectivo se contó")
    private Double efectivoContado;

    /** Opcional: por qué se explica la diferencia ("faltaba en el cambio de turno"). */
    private String descripcion;
}
