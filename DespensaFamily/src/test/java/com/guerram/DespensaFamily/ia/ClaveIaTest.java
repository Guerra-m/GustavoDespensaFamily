package com.guerram.DespensaFamily.ia;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holder mutable de la clave de Gemini (F5): permite que
 * ConfiguracionIaService la cambie en caliente y que
 * GeminiClienteExtraccionIa la lea siempre actualizada, sin reiniciar.
 */
class ClaveIaTest {

    @Test
    void arrancaSinConfigurarSiLaPropiedadEstaVacia() {
        ClaveIa claveIa = new ClaveIa("");
        assertFalse(claveIa.estaConfigurada());
        assertEquals("", claveIa.obtener());
    }

    @Test
    void arrancaConfiguradaConElValorInicial() {
        ClaveIa claveIa = new ClaveIa("una-clave-inicial-bien-larga");
        assertTrue(claveIa.estaConfigurada());
        assertEquals("una-clave-inicial-bien-larga", claveIa.obtener());
    }

    @Test
    void establecerCambiaElValorSinCrearOtraInstancia() {
        ClaveIa claveIa = new ClaveIa("");
        claveIa.establecer("clave-nueva");

        assertTrue(claveIa.estaConfigurada());
        assertEquals("clave-nueva", claveIa.obtener());
    }

    @Test
    void quitarDejaLaClaveVacia() {
        ClaveIa claveIa = new ClaveIa("clave-vieja");
        claveIa.quitar();

        assertFalse(claveIa.estaConfigurada());
        assertEquals("", claveIa.obtener());
    }

    @Test
    void unValorSoloEspaciosNoCuentaComoConfigurada() {
        ClaveIa claveIa = new ClaveIa("   ");
        assertFalse(claveIa.estaConfigurada());
    }
}
