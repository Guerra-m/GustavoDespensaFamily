package com.guerram.DespensaFamily.ia;

/**
 * Puente de test para construir un {@link ClaveIa} con origen forzado desde
 * fuera del paquete {@code ia}, sin abrir ese constructor a produccion (ver
 * ClaveIa: en produccion el origen lo decide unicamente la variable de
 * entorno real de la maquina).
 */
public final class ClaveIaTestSupport {

    private ClaveIaTestSupport() {
    }

    public static ClaveIa conVariableDeEntorno(String valor) {
        return new ClaveIa(valor, true);
    }
}
