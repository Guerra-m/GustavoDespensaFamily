package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freno para la fuerza bruta contra el login.
 *
 * <p>Medido en la auditoria del 01/09/2026: 20 intentos fallidos en 3 segundos,
 * sin bloqueo ni demora. Con el servidor escuchando en toda la red del local y
 * un usuario llamado "admin", eso es cuestion de minutos.
 *
 * <p>Sigue AUTN-03: <b>retardo progresivo, no bloqueo de cuenta</b>. El bloqueo
 * clasico ("cinco fallos y la cuenta queda inutilizable") es en si mismo un
 * ataque: cualquiera que sepa el nombre de usuario del dueno puede dejarlo
 * afuera de su propio negocio a proposito.
 */
class LimitadorDeIntentosTest {

    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-09-01T10:00:00Z"));
    private final LimitadorDeIntentos limitador = new LimitadorDeIntentos(ahora::get);

    private void avanzar(Duration cuanto) {
        ahora.set(ahora.get().plus(cuanto));
    }

    @Test
    void dejaPasarLosPrimerosIntentos() {
        // Equivocarse un par de veces al tipear es normal y no puede costar
        // una espera: el cajero esta atendiendo gente.
        for (int i = 0; i < 3; i++) {
            assertTrue(limitador.esperaPendiente("admin").isEmpty(),
                    "el intento " + (i + 1) + " no tendria que estar frenado");
            limitador.registrarFallo("admin");
        }
    }

    @Test
    void frenaDespuesDeVariosFallosSeguidos() {
        for (int i = 0; i < 4; i++) {
            limitador.registrarFallo("admin");
        }
        assertTrue(limitador.esperaPendiente("admin").isPresent(),
                "tras cuatro fallos seguidos tendria que haber una espera");
    }

    @Test
    void laEsperaCreceConCadaFalloNuevo() {
        for (int i = 0; i < 4; i++) {
            limitador.registrarFallo("admin");
        }
        Duration primera = limitador.esperaPendiente("admin").orElseThrow();

        limitador.registrarFallo("admin");
        Duration segunda = limitador.esperaPendiente("admin").orElseThrow();

        assertTrue(segunda.compareTo(primera) > 0,
                "la espera tendria que crecer: era " + primera + " y quedo " + segunda);
    }

    @Test
    void laEsperaTieneUnTecho() {
        // Sin techo, veinte fallos dejarian la cuenta inutilizable por dias:
        // seria el bloqueo de cuenta que AUTN-03 dice explicitamente que no.
        for (int i = 0; i < 40; i++) {
            limitador.registrarFallo("admin");
        }
        assertTrue(limitador.esperaPendiente("admin").orElseThrow()
                        .compareTo(LimitadorDeIntentos.ESPERA_MAXIMA) <= 0,
                "la espera supero el techo");
    }

    @Test
    void despuesDeEsperarSePuedeVolverAIntentar() {
        for (int i = 0; i < 4; i++) {
            limitador.registrarFallo("admin");
        }
        assertTrue(limitador.esperaPendiente("admin").isPresent());

        avanzar(LimitadorDeIntentos.ESPERA_MAXIMA.plusSeconds(1));

        assertTrue(limitador.esperaPendiente("admin").isEmpty(),
                "pasado el tiempo de espera tendria que poder intentar de nuevo");
    }

    @Test
    void unLoginCorrectoBorraElHistorialDeFallos() {
        for (int i = 0; i < 6; i++) {
            limitador.registrarFallo("admin");
        }
        avanzar(LimitadorDeIntentos.ESPERA_MAXIMA.plusSeconds(1));

        limitador.registrarExito("admin");

        for (int i = 0; i < 3; i++) {
            assertTrue(limitador.esperaPendiente("admin").isEmpty(),
                    "tras entrar bien, el contador tenia que arrancar de cero");
            limitador.registrarFallo("admin");
        }
    }

    @Test
    void cadaClaveSeCuentaPorSeparado() {
        // Que alguien machaque la cuenta "admin" no puede frenar a "maria",
        // que esta en la otra caja atendiendo.
        for (int i = 0; i < 10; i++) {
            limitador.registrarFallo("admin");
        }
        assertFalse(limitador.esperaPendiente("maria").isPresent(),
                "el freno de una cuenta se filtro a otra");
    }

    @Test
    void losFallosViejosNoCuentanParaSiempre() {
        // Tres errores hoy a la manana no pueden sumarse a uno de la tarde:
        // eso terminaria frenando al usuario legitimo despistado.
        for (int i = 0; i < 3; i++) {
            limitador.registrarFallo("admin");
        }
        avanzar(LimitadorDeIntentos.VENTANA_DE_OLVIDO.plusSeconds(1));

        limitador.registrarFallo("admin");

        assertTrue(limitador.esperaPendiente("admin").isEmpty(),
                "los fallos viejos tendrian que haberse olvidado");
    }

    @Test
    void elPrimerFalloTrasElOlvidoEmpiezaDeCero() {
        for (int i = 0; i < 8; i++) {
            limitador.registrarFallo("admin");
        }
        avanzar(LimitadorDeIntentos.VENTANA_DE_OLVIDO.plusSeconds(1));

        limitador.registrarFallo("admin");

        assertEquals(1, limitador.fallosRecientes("admin"));
    }
}
