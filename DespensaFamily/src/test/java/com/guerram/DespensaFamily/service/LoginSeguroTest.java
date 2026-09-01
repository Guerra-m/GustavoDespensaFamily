package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.CredencialesInvalidasException;
import com.guerram.DespensaFamily.config.DemasiadosIntentosException;
import com.guerram.DespensaFamily.dto.LoginResponseDTO;
import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Login: mismo error para todos los fallos, y freno a la fuerza bruta.
 *
 * <p>Dos agujeros medidos en la auditoria del 01/09/2026. Uno, el sistema
 * respondia 404 si el usuario no existia y 401 si existia con la contrasena
 * mal: eso deja averiguar que cuentas hay antes de empezar a adivinar
 * contrasenas. Dos, no habia ningun limite de intentos.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class LoginSeguroTest {

    private static final String USUARIO = "cajera-de-prueba";
    private static final String PASSWORD = "una-contrasena-larga-de-prueba";
    private static final String IP = "192.168.1.55";

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
                .password(passwordEncoder.encode(PASSWORD))
                .nombre("Cajera de prueba")
                .rol("EMPLEADO")
                .build());
        // Cada test arranca sin castigo acumulado del anterior.
        usuarioService.olvidarIntentos(USUARIO, IP);
    }

    @Test
    void entraConLaContrasenaCorrecta() {
        LoginResponseDTO respuesta = usuarioService.login(USUARIO, PASSWORD, IP);

        assertNotNull(respuesta.getToken());
        assertEquals("EMPLEADO", respuesta.getRol());
    }

    @Test
    void elUsuarioInexistenteYLaContrasenaMalaDanExactamenteElMismoError() {
        // Si se distinguen, se puede armar la lista de usuarios del negocio
        // probando nombres, y recien despues atacar contrasenas.
        CredencialesInvalidasException porUsuario = assertThrows(
                CredencialesInvalidasException.class,
                () -> usuarioService.login("no-existe-este-usuario", "loquesea", IP));

        CredencialesInvalidasException porPassword = assertThrows(
                CredencialesInvalidasException.class,
                () -> usuarioService.login(USUARIO, "contrasena-equivocada", IP));

        assertEquals(porUsuario.getMessage(), porPassword.getMessage(),
                "los dos casos tienen que dar el mismo mensaje");
    }

    @Test
    void frenaDespuesDeVariosIntentosFallidos() {
        for (int i = 0; i < 4; i++) {
            assertThrows(RuntimeException.class,
                    () -> usuarioService.login(USUARIO, "mal", IP));
        }

        assertThrows(DemasiadosIntentosException.class,
                () -> usuarioService.login(USUARIO, "mal", IP),
                "tras varios fallos seguidos tendria que frenar");
    }

    @Test
    void elFrenoTambienAplicaConLaContrasenaCorrecta() {
        // Si al llegar al limite se aceptara la contrasena buena, el atacante
        // que la acaba de adivinar entra igual y el freno no sirvio de nada.
        for (int i = 0; i < 5; i++) {
            assertThrows(RuntimeException.class,
                    () -> usuarioService.login(USUARIO, "mal", IP));
        }

        assertThrows(DemasiadosIntentosException.class,
                () -> usuarioService.login(USUARIO, PASSWORD, IP));
    }

    @Test
    void machacarUnaCuentaNoDejaAfueraALasDemas() {
        // AUTN-03: el freno no puede volverse un arma para bloquear la cuenta
        // de otro. La cajera de la otra caja tiene que poder entrar.
        for (int i = 0; i < 8; i++) {
            assertThrows(RuntimeException.class,
                    () -> usuarioService.login(USUARIO, "mal", "10.0.0.99"));
        }

        usuarioRepository.save(Usuario.builder()
                .username("otra-cajera")
                .password(passwordEncoder.encode(PASSWORD))
                .nombre("Otra")
                .rol("EMPLEADO")
                .build());
        usuarioService.olvidarIntentos("otra-cajera", IP);

        assertNotNull(usuarioService.login("otra-cajera", PASSWORD, IP).getToken());
        usuarioRepository.findByUsername("otra-cajera").ifPresent(usuarioRepository::delete);
    }

    @Test
    void elLoginCorrectoInformaSiHayQueCambiarLaContrasena() {
        Usuario u = usuarioRepository.findByUsername(USUARIO).orElseThrow();
        u.setDebeCambiarPassword(true);
        usuarioRepository.save(u);

        assertTrue(usuarioService.login(USUARIO, PASSWORD, IP).isDebeCambiarPassword());
    }

    @Test
    void cambiarLaContrasenaLevantaLaObligacion() {
        Usuario u = usuarioRepository.findByUsername(USUARIO).orElseThrow();
        u.setDebeCambiarPassword(true);
        usuarioRepository.save(u);

        usuarioService.cambiarPasswordDe(USUARIO, PASSWORD, "otra-contrasena-nueva-larga");

        assertFalse(usuarioRepository.findByUsername(USUARIO).orElseThrow().isDebeCambiarPassword());
    }
}
