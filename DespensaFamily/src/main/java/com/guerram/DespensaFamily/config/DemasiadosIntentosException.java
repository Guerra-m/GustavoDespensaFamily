package com.guerram.DespensaFamily.config;

import java.time.Duration;

/**
 * Hubo demasiados intentos fallidos seguidos y hay que esperar.
 *
 * <p>No es un bloqueo de cuenta (ver LimitadorDeIntentos y AUTN-03): la espera
 * se vence sola y el usuario legitimo vuelve a entrar sin que nadie tenga que
 * desbloquearlo.
 */
public class DemasiadosIntentosException extends RuntimeException {

    private final Duration espera;

    public DemasiadosIntentosException(Duration espera) {
        super("Demasiados intentos fallidos. Esperá " + Math.max(1, espera.toSeconds()) + " segundos y probá de nuevo.");
        this.espera = espera;
    }

    public Duration getEspera() {
        return espera;
    }
}
