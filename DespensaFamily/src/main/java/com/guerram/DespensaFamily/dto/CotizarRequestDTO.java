package com.guerram.DespensaFamily.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Mismo shape que VentaRequestDTO pero sin exigir metodoPago: cotizar no
 * registra nada en caja, asi que no hace falta saber como se va a cobrar
 * para decir cuanto sale. El campo se acepta igual (opcional) para que el
 * frontend pueda mandar el mismo payload que usaria despues para confirmar.
 */
@Data
public class CotizarRequestDTO {
    @NotEmpty
    private List<DetalleVentaRequestDTO> detalles;

    private com.guerram.DespensaFamily.model.MetodoPago metodoPago;
}
