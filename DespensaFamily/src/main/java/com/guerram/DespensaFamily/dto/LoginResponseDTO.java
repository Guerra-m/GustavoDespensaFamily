package com.guerram.DespensaFamily.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDTO {
    private String message;

    // No viaja en el JSON: sale del servidor dentro de una cookie HttpOnly (ver
    // CookieDeSesion). Si volviera en el cuerpo, el frontend podria guardarlo
    // en localStorage sin querer y quedariamos con la sesion en los dos lados a
    // la vez, que es el peor de los mundos (FE-02, "mezclar estrategias").
    @JsonIgnore
    private String token;
    private String rol;

    // El admin que crea el instalador entra con una contrasena generada al
    // azar. Con esto el frontend sabe que tiene que mandarlo a cambiarla antes
    // de dejarlo usar el sistema.
    private boolean debeCambiarPassword;

    // Para que el frontend sepa que mostrar/ocultar sin depender de decodificar
    // el JWT (que ademas es HttpOnly, no deberia poder leerlo). No son
    // credenciales — mismo criterio que ya vale para "rol" en este DTO.
    private boolean puedeGestionarProductos;
    private boolean puedeVerCaja;
}
