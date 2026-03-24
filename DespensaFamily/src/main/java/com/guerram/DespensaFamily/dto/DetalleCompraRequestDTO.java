package com.guerram.DespensaFamily.dto;

import jakarta.validation.constraints.Min;
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

    @Min(1)
    private int cantidad;

    @Min(0)
    private double precioCosto;
}
