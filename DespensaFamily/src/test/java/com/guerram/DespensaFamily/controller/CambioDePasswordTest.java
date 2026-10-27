package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import com.guerram.DespensaFamily.service.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DF-BUG-007: escribir mal la contraseña actual en Mi Perfil cerraba la sesion
 * sin decir nada.
 *
 * <p>El backend respondia 401 (la heuristica de GlobalExceptionHandler mandaba
 * todo mensaje con "incorrecta" a 401) y el interceptor del frontend
 * (auth.service.ts) trata todo 401 como sesion vencida: borra los datos y
 * manda al login. El 401 queda reservado para "no hay sesion valida"; un dato
 * mal escrito en un formulario es un 400 con su mensaje.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class CambioDePasswordTest {

    private static final String USUARIO = "cambia-su-clave";
    private static final String ACTUAL = "la-actual-de-prueba-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final RequestPostProcessor comoElUsuario = user(USUARIO).roles("EMPLEADO");

    private Long id;

    @BeforeEach
    void crearUsuario() {
        usuarioRepository.findByUsername(USUARIO).ifPresent(usuarioRepository::delete);
        id = usuarioRepository.save(Usuario.builder()
                .username(USUARIO)
                .password(passwordEncoder.encode(ACTUAL))
                .nombre("Cambia su clave")
                .rol("EMPLEADO")
                .build()).getId();
    }

    @Autowired
    private UsuarioService usuarioService;

    // El login fallido de abajo suma un intento al freno por IP (127.0.0.1
    // es la de MockMvc para todos los tests del contexto): se limpia.
    @AfterEach
    void olvidarIntentos() {
        usuarioService.olvidarIntentos(USUARIO, "127.0.0.1");
    }

    private String cuerpo(String actual, String nueva) {
        return "{\"passwordActual\":\"" + actual + "\",\"passwordNueva\":\"" + nueva + "\"}";
    }

    @Test
    void laContrasenaActualMalEscritaEsUn400ConMensajeYNoUn401() throws Exception {
        mockMvc.perform(patch("/usuario/" + id + "/password").with(comoElUsuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("no-es-la-actual", "una-nueva-bien-larga-456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Contraseña actual incorrecta"));

        // Y la contraseña no cambio.
        String hash = usuarioRepository.findByUsername(USUARIO).orElseThrow().getPassword();
        assertThat(passwordEncoder.matches(ACTUAL, hash)).isTrue();
    }

    @Test
    void elLoginConContrasenaMalEscritaSigueSiendo401() throws Exception {
        // El otro lado de la regla: el login fallido SI es 401 (tiene su
        // propio handler) y el interceptor del frontend lo exceptua.
        mockMvc.perform(post("/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USUARIO + "\",\"password\":\"no-es-la-actual\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void conLaContrasenaActualCorrectaLaCambia() throws Exception {
        mockMvc.perform(patch("/usuario/" + id + "/password").with(comoElUsuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ACTUAL, "una-nueva-bien-larga-456")))
                .andExpect(status().isOk());

        String hash = usuarioRepository.findByUsername(USUARIO).orElseThrow().getPassword();
        assertThat(passwordEncoder.matches("una-nueva-bien-larga-456", hash)).isTrue();
    }
}
