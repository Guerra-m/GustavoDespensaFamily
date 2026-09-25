package com.guerram.DespensaFamily.config;

import org.springframework.http.HttpStatus;

/**
 * DF-DEBT-003: el pedido es valido (no es un 404) pero choca con una regla
 * del negocio — stock insuficiente, un descuento duplicado, una venta ya
 * anulada, un valor fuera de rango, etc. Reemplaza el
 * {@code RuntimeException} generico que antes caia en el 400 "por
 * descarte" de GlobalExceptionHandler.
 *
 * <p>Default 400: es lo que ya devolvia el handler para estos mensajes.
 * El constructor con {@link HttpStatus} existe para el caso "segun
 * corresponda" (algun error de negocio que en realidad es un conflicto de
 * estado, 409, y no un pedido invalido, 400) sin tener que crear una clase
 * nueva por cada variante.
 */
public class ReglaDeNegocioException extends RuntimeException {

    private final HttpStatus status;

    public ReglaDeNegocioException(String mensaje) {
        this(mensaje, HttpStatus.BAD_REQUEST);
    }

    public ReglaDeNegocioException(String mensaje, HttpStatus status) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
