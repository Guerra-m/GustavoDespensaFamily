package com.guerram.DespensaFamily.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CompraRequestDTO {
    @NotBlank(message = "El proveedor es obligatorio")
    private String proveedor;

    @NotEmpty(message = "La compra debe tener al menos un producto")
    @Valid
    private List<DetalleCompraRequestDTO> detalles;
}
