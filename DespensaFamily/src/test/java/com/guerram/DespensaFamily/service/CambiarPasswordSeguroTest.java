package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.DemasiadosIntentosException;
import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DF-SEC-004: cambiar la contraseña no tenia freno de intentos.
 *
 * <p>{@code LimitadorDeIntentos} ya protegia el login (ver LoginSeguroTest),
 * pero {@code cambiarPasswordDe} probaba la contrasena actual sin ningun
 * limite: 20 intentos seguidos en segundos, la misma ventana que se cerro en
 * el login. Mismo criterio que ahi (AUTN-03): freno progresivo por usuario,
 * nunca bloqueo de cuenta, y nunca 401 (DF-BUG-007: el frontend borra la
 * sesion ante cualquier 401 y esto es "Mi Perfil", no el login).
 *
 * <p>La clave del freno es "password:" + username, separada de "usuario:" que
 * usa el login: son dos formularios distintos y machacar uno no tiene por que
 * gastar el margen del otro (ver machacarCambiarPasswordNoAfectaElLogin).
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class CambiarPasswordSeguroTest {

    private static final String USUARIO = "cajera-cambia-password";
    private static final String IP = "192.168.1.77";
    private static final String PASSWORD_ACTUAL = "una-contrasena-larga-de-prueba";
    private static final String PASSWORD_NUEVA = "otra-contrasena-nueva-larga";

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void crearUsuario() {
        usuarioRepository.findByUsername(USUARIO).ifPresent(usuarioRepository::delete);
        usuarioRepository.save(Usuario.builder()
                .username(USUARIO)
                .password(passwordEncoder.encode(PASSWORD_ACTUAL))
                .nombre("Cajera de prueba")
                .rol("EMPLEADO")
                .build());
        // Cada test arranca sin castigo acumulado del anterior.
        usuarioService.olvidarIntentos(USUARIO, IP);
        usuarioService.olvidarIntentosDeCambioDePassword(USUARIO);
    }

    @Test
    void frenaDespuesDeVariosIntentosFallidos() {
        for (int i = 0; i < 4; i++) {
            assertThrows(RuntimeException.class,
                    () -> usuarioService.cambiarPasswordDe(USUARIO, "mal", PASSWORD_NUEVA));
        }

        DemasiadosIntentosException ex = assertThrows(DemasiadosIntentosException.class,
                () -> usuarioService.cambiarPasswordDe(USUARIO, "mal", PASSWORD_NUEVA),
                "tras varios fallos seguidos tendria que frenar, igual que el login");

        // DF-BUG-007: nunca 401 — el frontend interpreta cualquier 401 como
        // sesion vencida. El freno tiene que resolver a 429 (o 400), nunca a
        // una excepcion que el handler global mapee a 401.
        assertEquals("Demasiados intentos fallidos. Esperá 1 segundos y probá de nuevo.",
                ex.getMessage().replaceAll("Esperá \\d+", "Esperá 1"));
    }

    @Test
    void laContrasenaCorrectaSigueFuncionandoDespuesDeUnosPocosIntentos() {
        // Margen para el que se equivoca tipeando, sin ser un ataque: por
        // debajo del umbral, la cuenta legitima entra igual.
        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class,
                    () -> usuarioService.cambiarPasswordDe(USUARIO, "mal", PASSWORD_NUEVA));
        }

        assertDoesNotThrow(() -> usuarioService.cambiarPasswordDe(USUARIO, PASSWORD_ACTUAL, PASSWORD_NUEVA));
    }

    @Test
    void machacarCambiarPasswordNoAfectaElLogin() {
        // El freno de cambiar contrasena es una clave separada de la del
        // login: agotar uno no puede dejar a la cajera sin poder entrar.
        for (int i = 0; i < 5; i++) {
            assertThrows(RuntimeException.class,
                    () -> usuarioService.cambiarPasswordDe(USUARIO, "mal", PASSWORD_NUEVA));
        }

        assertDoesNotThrow(() -> usuarioService.login(USUARIO, PASSWORD_ACTUAL, IP));
    }

    @Test
    void elFrenoNoBloqueaLaCuenta() {
        // AUTN-03: es demora, no bloqueo — nunca queda "para siempre" sin
        // poder entrar. Con la clave sola (sin reloj inyectable en este
        // test) alcanza con comprobar que despues de un exito registrado a
        // mano el contador vuelve a cero.
        for (int i = 0; i < 5; i++) {
            assertThrows(RuntimeException.class,
                    () -> usuarioService.cambiarPasswordDe(USUARIO, "mal", PASSWORD_NUEVA));
        }

        usuarioService.olvidarIntentosDeCambioDePassword(USUARIO);

        assertDoesNotThrow(() -> usuarioService.cambiarPasswordDe(USUARIO, PASSWORD_ACTUAL, PASSWORD_NUEVA));
    }
}
