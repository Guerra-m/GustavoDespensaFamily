package com.guerram.DespensaFamily.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    @DecimalMin("0.0")
    private BigDecimal precioVenta;

    @DecimalMin("0.0")
    private BigDecimal precioCosto;

    @DecimalMin("0.0")
    private BigDecimal porcentajeGanancia;

    @DecimalMin("0.0")
    private BigDecimal stock;

    @Pattern(regexp = "UNIDAD|PESO", message = "tipoCantidad debe ser UNIDAD o PESO")
    private String tipoCantidad;
    private String unidadMedida;
    private BigDecimal fraccionMinima;
    private Integer unidadesPorPaquete;
    private String codigoBarras;

    private String imagenUrl;


    private Long categoriaId;
    private String proveedor;


}
