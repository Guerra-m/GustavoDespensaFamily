package com.guerram.DespensaFamily.config;

/**
 * DF-DEBT-003: el asistente de IA (Gemini) no esta configurado, no se pudo
 * conectar, tardo demasiado o se agoto la cuota gratuita — siempre 503:
 * el pedido del cliente esta bien, el que fallo es un tercero. Antes
 * GlobalExceptionHandler detectaba esto buscando frases como "no esta
 * configurado" o "limite gratuito" en el mensaje de una RuntimeException
 * generica.
 */
public class ServicioExternoNoDisponibleException extends RuntimeException {

    public ServicioExternoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
