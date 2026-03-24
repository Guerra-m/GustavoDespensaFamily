package com.guerram.DespensaFamily.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoDTO {
    private Long id;

    @NotBlank
    private String nombre;

    @Min(0)
    private double precioVenta;

    @Min(0)
    private double precioCosto;

    @Min(0)
    private double porcentajeGanancia;

    @Min(0)
    private int stock;
    private String codigoBarras;

    private String imagenUrl;


    private Long categoriaId;
    private String proveedor;


}
