package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrasena inicial del administrador, distinta en cada instalacion.
 *
 * <p>Antes el primer admin salia con "admin123" clavado en el codigo: publica,
 * identica en todas las copias, y con la aplicacion accesible desde el wifi del
 * local eso alcanza para entrar. Esta clase genera una contrasena al azar que
 * ademas tiene que poder dictarse por telefono y copiarse a mano de un cartel,
 * asi que el alfabeto evita los caracteres que se confunden al leerlos.
 */
class CredencialInicialTest {

    @Test
    void dosContrasenasNuncaSonIguales() {
        Set<String> generadas = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            generadas.add(CredencialInicial.generar());
        }
        assertEquals(200, generadas.size(), "se repitio alguna contrasena en 200 generaciones");
    }

    @Test
    void tieneLargoSuficienteParaNoSerAdivinable() {
        // Sin bloqueo de cuenta, la defensa real es el espacio de busqueda.
        // 16 caracteres del alfabeto reducido dan de sobra.
        assertTrue(CredencialInicial.generar().replace("-", "").length() >= 16);
    }

    @Test
    void noUsaCaracteresQueSeConfundenAlLeerlos() {
        // Quien instala le dicta esto al dueno del negocio o lo copia de un
        // cartel: 0/O, 1/l/I y 5/S son errores de tipeo garantizados.
        String prohibidos = "0O1lI5S";
        for (int i = 0; i < 100; i++) {
            String generada = CredencialInicial.generar();
            for (char c : prohibidos.toCharArray()) {
                assertFalse(generada.indexOf(c) >= 0,
                        "la contrasena " + generada + " tiene el caracter ambiguo '" + c + "'");
            }
        }
    }

    @Test
    void vieneEnGruposSeparadosParaPoderLeerlaEnVozAlta() {
        assertTrue(CredencialInicial.generar().contains("-"));
    }

    @Test
    void noUsaCaracteresQueRompanUnArchivoDePropiedades() {
        // Termina escrita en un archivo de texto al lado del secreto JWT.
        for (int i = 0; i < 100; i++) {
            String generada = CredencialInicial.generar();
            assertFalse(generada.contains("\\") || generada.contains("=") || generada.contains(":"),
                    "contrasena problematica para un .properties: " + generada);
        }
    }
}
