package com.guerram.DespensaFamily.config;

/**
 * Falla de login, sin decir por que.
 *
 * <p>Se usa el mismo mensaje tanto si el usuario no existe como si la
 * contrasena esta mal. Distinguirlos —que es lo que hacia antes, con 404 y
 * 401— permite armar la lista de usuarios reales del negocio probando nombres,
 * y recien despues empezar a adivinar contrasenas contra las cuentas que se
 * sabe que existen.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Usuario o contraseña incorrectos");
    }
}
