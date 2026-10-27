package com.guerram.DespensaFamily.config;

/**
 * Se intento cambiar o quitar la clave de Gemini desde la app, pero
 * GEMINI_API_KEY esta definida como variable de entorno de Windows: esa
 * gana siempre (ver ClaveIa), asi que la unica forma de cambiarla es ahi.
 */
public class ClaveIaPorVariableDeEntornoException extends RuntimeException {

    public ClaveIaPorVariableDeEntornoException() {
        super("La clave está definida por una variable de entorno de Windows; cambiala ahí.");
    }
}
