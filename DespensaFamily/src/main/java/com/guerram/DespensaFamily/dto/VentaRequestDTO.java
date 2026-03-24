package com.guerram.DespensaFamily.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class VentaRequestDTO {
    @NotEmpty
    private List<DetalleVentaRequestDTO> detalles;
}
