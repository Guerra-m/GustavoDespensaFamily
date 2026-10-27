package com.guerram.DespensaFamily.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DetalleCompraDTO {
    private Long id;

    @NotNull(message = "La cantidad es obligatoria")
    @Positive(message = "La cantidad debe ser mayor a 0")
    private BigDecimal cantidad;

    @NotNull(message = "El precio de costo es obligatorio")
    @PositiveOrZero(message = "El precio de costo no puede ser negativo")
    private BigDecimal precioCosto;
    private BigDecimal subtotal;
    private Long compraId;
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
}
