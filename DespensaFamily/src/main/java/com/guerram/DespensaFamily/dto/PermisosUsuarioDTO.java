package com.guerram.DespensaFamily.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class PermisosUsuarioDTO {
    private boolean puedeGestionarProductos;
    private boolean puedeVerCaja;
}
