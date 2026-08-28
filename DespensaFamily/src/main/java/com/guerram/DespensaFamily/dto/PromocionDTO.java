package com.guerram.DespensaFamily.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromocionDTO {
    private Long id;

    @NotNull
    private Long productoId;

    private String productoNombre;

    @Min(2)
    private int cantidadLleva;

    @Min(1)
    private int cantidadPaga;

    private boolean activo;
}
