package com.guerram.DespensaFamily.dto.empleados;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DiaTrabajadoDTO {
    private Long empleadoId;
    private LocalDate fecha;
}
