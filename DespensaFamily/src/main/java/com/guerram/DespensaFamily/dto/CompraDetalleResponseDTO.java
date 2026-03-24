package com.guerram.DespensaFamily.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraDetalleResponseDTO {
    private Long id;
    private LocalDate fechaHora;
    private String proveedor;
    private List<DetalleCompraDTO> detalles;
}
