package com.guerram.DespensaFamily.dto;

import com.guerram.DespensaFamily.model.AlcanceDescuento;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DescuentoDTO {
    private Long id;

    @NotNull
    private AlcanceDescuento alcance;

    private Long productoId;
    private String productoNombre;

    private Long categoriaId;
    private String categoriaNombre;

    @DecimalMin(value = "1", message = "El descuento tiene que ser mayor a 0%")
    @DecimalMax(value = "99", message = "El descuento tiene que ser menor a 100%")
    private double porcentaje;

    private boolean activo;
}
