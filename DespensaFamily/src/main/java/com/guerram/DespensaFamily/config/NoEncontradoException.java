package com.guerram.DespensaFamily.config;

/**
 * DF-DEBT-003: "esto que pediste no existe" — siempre 404. Reemplaza el
 * {@code RuntimeException("X no encontrado/a")} que antes GlobalExceptionHandler
 * detectaba por texto (buscando "no encontrado"/"no encontrada" en el
 * mensaje). El mensaje sigue siendo el mismo que ve el frontend: lo unico
 * que cambia es que ahora el status lo decide el TIPO de la excepcion, no
 * una heuristica sobre el string.
 */
public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
