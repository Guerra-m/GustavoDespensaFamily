package com.guerram.DespensaFamily.dto.ia;

/**
 * Estado del asistente de IA para Ajustes. Nunca lleva la clave, ni siquiera
 * enmascarada (SEC-01/SEC-05): solo si esta configurada y de donde sale.
 */
public record EstadoIaDTO(boolean configurada, String origen) {

    public static final String ORIGEN_VARIABLE_DE_ENTORNO = "variable-de-entorno";
    public static final String ORIGEN_ARCHIVO = "archivo";
    public static final String ORIGEN_NINGUNO = "ninguno";
}
