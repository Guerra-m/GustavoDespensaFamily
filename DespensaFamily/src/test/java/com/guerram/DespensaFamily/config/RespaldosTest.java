package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retencion de respaldos (F4). La regla tiene que ser conservadora: es la
 * unica copia de los datos del negocio, asi que ante la duda no se borra.
 */
class RespaldosTest {

    @Test
    void reconoceLosRespaldosAutomaticos() {
        assertTrue(Respaldos.esRespaldo("despensa-2026-08-28_23-49.zip"));
        assertTrue(Respaldos.esRespaldo("despensa-2026-01-01_00-00.zip"));
    }

    @Test
    void noTocaArchivosQueNoGeneroElRespaldoAutomatico() {
        // Copias hechas a mano antes de una migracion, por ejemplo.
        assertFalse(Respaldos.esRespaldo("despensa-previo-limpieza.mv.db"));
        assertFalse(Respaldos.esRespaldo("despensa.mv.db"));
        assertFalse(Respaldos.esRespaldo("notas.txt"));
        assertFalse(Respaldos.esRespaldo("despensa-2026-08-28_23-49.zip.bak"));
    }

    @Test
    void conMenosRespaldosQueElLimiteNoBorraNada() {
        List<String> archivos = List.of(
                "despensa-2026-08-26_10-00.zip",
                "despensa-2026-08-27_10-00.zip");

        assertEquals(List.of(), Respaldos.aEliminar(archivos, 14));
    }

    @Test
    void borraLosMasViejosYConservaLosMasNuevos() {
        List<String> archivos = List.of(
                "despensa-2026-08-26_10-00.zip",
                "despensa-2026-08-27_10-00.zip",
                "despensa-2026-08-28_10-00.zip");

        assertEquals(
                List.of("despensa-2026-08-26_10-00.zip"),
                Respaldos.aEliminar(archivos, 2));
    }

    @Test
    void ordenaPorFechaAunqueLleguenDesordenados() {
        List<String> archivos = List.of(
                "despensa-2026-08-28_10-00.zip",
                "despensa-2026-08-26_10-00.zip",
                "despensa-2026-08-27_10-00.zip");

        assertEquals(
                List.of("despensa-2026-08-26_10-00.zip", "despensa-2026-08-27_10-00.zip"),
                Respaldos.aEliminar(archivos, 1));
    }

    @Test
    void losArchivosAjenosNoCuentanNiSeBorran() {
        List<String> archivos = List.of(
                "despensa-previo-limpieza.mv.db",
                "despensa-2026-08-26_10-00.zip",
                "despensa-2026-08-27_10-00.zip",
                "un-pdf-que-alguien-dejo.pdf");

        assertEquals(
                List.of("despensa-2026-08-26_10-00.zip"),
                Respaldos.aEliminar(archivos, 1));
    }

    @Test
    void unLimiteInvalidoNoBorraNada() {
        List<String> archivos = List.of(
                "despensa-2026-08-26_10-00.zip",
                "despensa-2026-08-27_10-00.zip");

        assertEquals(List.of(), Respaldos.aEliminar(archivos, 0));
        assertEquals(List.of(), Respaldos.aEliminar(archivos, -3));
    }
}
