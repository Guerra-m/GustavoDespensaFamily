package com.guerram.DespensaFamily.config;

import java.util.Set;

/**
 * Que contrasenas se aceptan al crear un usuario o al cambiar la propia.
 *
 * <p>Sigue AUTN-02: la entropia real la aporta el largo, no las reglas de
 * "una mayuscula, un numero y un simbolo", que la gente esquiva de forma
 * predecible ({@code Password1!}). Asi que hay un minimo de largo y una lista
 * corta de las contrasenas que este sistema ya tuvo publicadas.
 */
public final class PoliticaDePassword {

    public static final int LARGO_MINIMO = 10;

    /**
     * Las que vinieron en el codigo de versiones anteriores y las obvias del
     * rubro. No pretende ser un diccionario completo: son justo las que un
     * atacante probaria primero contra esta aplicacion.
     */
    private static final Set<String> PROHIBIDAS = Set.of(
            "admin123", "empleado123", "despensa123", "despensafamily",
            "contrasena", "contraseña", "password", "12345678", "1234567890"
    );

    private PoliticaDePassword() {
    }

    public static void validar(String password) {
        if (password == null || password.isBlank()) {
            throw new RuntimeException("La contraseña no puede estar vacía");
        }
        if (password.length() < LARGO_MINIMO) {
            throw new RuntimeException(
                    "La contraseña tiene que tener al menos " + LARGO_MINIMO + " caracteres");
        }
        if (PROHIBIDAS.contains(password.toLowerCase())) {
            throw new RuntimeException("Esa contraseña es demasiado conocida, elegí otra");
        }
    }
}
