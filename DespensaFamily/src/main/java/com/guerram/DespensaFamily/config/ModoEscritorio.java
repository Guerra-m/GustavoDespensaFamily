package com.guerram.DespensaFamily.config;

/**
 * Quien se encarga de mostrar la ventana de la aplicacion.
 *
 * <p>Se elige con {@code despensa.escritorio.modo} y lo unico que cambia es el
 * launcher: el servidor, la base y los respaldos son identicos en los dos modos.
 */
public enum ModoEscritorio {

    /**
     * Java abre el navegador del sistema al estar lista. Es el modo historico y
     * el que queda si alguien corre el jar a mano.
     */
    NAVEGADOR,

    /**
     * La ventana la pone el programa de escritorio, que arranca este servidor
     * como proceso hijo. Java no abre nada: solo sirve y deja el icono en la
     * bandeja para respaldar o salir.
     */
    VENTANA;

    /** Tolerante a proposito: un valor mal escrito no puede dejar la app sin forma de abrirse. */
    public static ModoEscritorio desde(String valor) {
        if (valor == null) {
            return NAVEGADOR;
        }
        return VENTANA.name().equalsIgnoreCase(valor.trim()) ? VENTANA : NAVEGADOR;
    }

    public boolean abreElNavegador() {
        return this == NAVEGADOR;
    }
}
