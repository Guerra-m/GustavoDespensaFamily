package com.guerram.DespensaFamily.dto.empleados;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class EmpleadoDTO {
    private Long id;
    private String nombre;
    private boolean activo;
    private String username;
}
