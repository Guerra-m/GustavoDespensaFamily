package com.guerram.DespensaFamily.model;

import java.util.Set;

/**
 * Los dos roles que existen.
 *
 * <p>Antes el rol llegaba como texto libre desde el DTO y se guardaba tal cual.
 * Un "Admin" o un "admin" con distinta capitalizacion se guardaba sin chistar y
 * despues no matcheaba contra {@code hasRole("ADMIN")}: el usuario quedaba
 * creado pero sin poder hacer nada, y sin ningun error que lo explicara.
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String EMPLEADO = "EMPLEADO";

    private static final Set<String> VALIDOS = Set.of(ADMIN, EMPLEADO);

    private Roles() {
    }

    /** Normaliza y valida; sin rol, el que menos permisos tiene. */
    public static String validar(String rol) {
        if (rol == null || rol.isBlank()) {
            return EMPLEADO;
        }
        String normalizado = rol.trim().toUpperCase();
        if (!VALIDOS.contains(normalizado)) {
            throw new RuntimeException("Rol inválido: tiene que ser ADMIN o EMPLEADO");
        }
        return normalizado;
    }
}
