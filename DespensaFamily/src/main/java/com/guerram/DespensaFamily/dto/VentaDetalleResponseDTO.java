package com.guerram.DespensaFamily.dto;

import com.guerram.DespensaFamily.model.EstadoVenta;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDetalleResponseDTO {
    private Long id;
    private LocalDate fechaHora;
    private double total;
    private List<DetalleVentaDTO> detalles;
    private EstadoVenta estado;
}
