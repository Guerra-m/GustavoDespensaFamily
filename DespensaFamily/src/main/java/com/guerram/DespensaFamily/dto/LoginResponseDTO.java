package com.guerram.DespensaFamily.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDTO {
    private String message;
    private String token;
    private String rol;

    // El admin que crea el instalador entra con una contrasena generada al
    // azar. Con esto el frontend sabe que tiene que mandarlo a cambiarla antes
    // de dejarlo usar el sistema.
    private boolean debeCambiarPassword;
}
