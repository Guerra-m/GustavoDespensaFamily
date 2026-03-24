package com.guerram.DespensaFamily.dto;

import com.guerram.DespensaFamily.model.EstadoVenta;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDTO {
    private Long id;
    private LocalDate fechaHora;
    private double total;
    private EstadoVenta estado;
}
