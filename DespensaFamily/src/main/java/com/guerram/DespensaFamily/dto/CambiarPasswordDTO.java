package com.guerram.DespensaFamily.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarPasswordDTO {

    @NotBlank
    private String passwordActual;

    @NotBlank
    private String PasswordNueva;
}