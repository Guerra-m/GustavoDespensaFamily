package com.guerram.DespensaFamily.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCompraRequestDTO {
    @NotNull
    private Long productoId;

    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0")
    private double cantidad;

    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    private double precioCosto;
}
