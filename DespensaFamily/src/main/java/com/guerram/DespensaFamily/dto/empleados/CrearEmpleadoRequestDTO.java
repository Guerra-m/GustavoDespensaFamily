package com.guerram.DespensaFamily.dto.empleados;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CrearEmpleadoRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    /** Opcional: si además usa el sistema, su username. */
    private String username;
}
