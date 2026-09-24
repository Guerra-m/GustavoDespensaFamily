package com.guerram.DespensaFamily.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class VentaRequestDTO {
    @NotEmpty
    private List<DetalleVentaRequestDTO> detalles;

    @jakarta.validation.constraints.NotNull(message = "El método de pago es obligatorio")
    private com.guerram.DespensaFamily.model.MetodoPago metodoPago;

    /** Opcional, para el ticket. Texto libre. */
    private String cliente;
}
