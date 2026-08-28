package com.guerram.DespensaFamily.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarPasswordDTO {

    @NotBlank
    private String passwordActual;

    // Antes decía "PasswordNueva" (P mayúscula). Con Jackson 3, el binding
    // de JSON usa el nombre literal del campo (case-sensitive) — el
    // frontend manda "passwordNueva" (camelCase estándar) y nunca
    // matcheaba, así que este campo llegaba SIEMPRE null. El servicio
    // igual encodeaba y guardaba ese null sin fallar, dejando la cuenta
    // con password NULL en la base — bug real que rompía la cuenta de
    // cualquiera que cambiara su contraseña, no solo un problema de tipeo.
    @NotBlank
    private String passwordNueva;
}