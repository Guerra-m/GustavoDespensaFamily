package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quien pone la ventana. En modo NAVEGADOR la pone Java abriendo el navegador
 * del sistema (como venia siendo); en modo VENTANA la pone el programa de
 * escritorio y Java se limita a servir y a dejar el icono en la bandeja.
 */
class ModoEscritorioTest {

    @Test
    void sinConfigurarNadaSigueSiendoElNavegador() {
        assertEquals(ModoEscritorio.NAVEGADOR, ModoEscritorio.desde(null));
        assertEquals(ModoEscritorio.NAVEGADOR, ModoEscritorio.desde(""));
        assertEquals(ModoEscritorio.NAVEGADOR, ModoEscritorio.desde("   "));
    }

    @Test
    void reconoceLosDosModosSinImportarMayusculasNiEspacios() {
        assertEquals(ModoEscritorio.VENTANA, ModoEscritorio.desde("ventana"));
        assertEquals(ModoEscritorio.VENTANA, ModoEscritorio.desde("  VENTANA "));
        assertEquals(ModoEscritorio.NAVEGADOR, ModoEscritorio.desde("Navegador"));
    }

    // Un valor mal escrito en config.properties no puede dejar la app sin
    // ninguna forma de abrirse: ante la duda, el comportamiento de siempre.
    @Test
    void unValorDesconocidoCaeEnElNavegador() {
        assertEquals(ModoEscritorio.NAVEGADOR, ModoEscritorio.desde("electron"));
        assertEquals(ModoEscritorio.NAVEGADOR, ModoEscritorio.desde("si"));
    }

    @Test
    void soloElModoNavegadorAbreElNavegadorYExplicaComoCerrar() {
        assertTrue(ModoEscritorio.NAVEGADOR.abreElNavegador());
        assertFalse(ModoEscritorio.VENTANA.abreElNavegador());
    }
}
