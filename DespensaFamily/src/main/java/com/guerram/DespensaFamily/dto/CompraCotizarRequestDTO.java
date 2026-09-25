package com.guerram.DespensaFamily.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Mismo shape que CompraRequestDTO pero sin proveedor/pagada/metodoPago:
 * previsualizar no registra nada, solo necesita saber que se compraria y a
 * que costo para mostrar el precio de venta resultante (ver
 * CompraService.previsualizar).
 */
@Data
public class CompraCotizarRequestDTO {
    @NotEmpty(message = "La compra debe tener al menos un producto")
    @Valid
    private List<DetalleCompraRequestDTO> detalles;
}
