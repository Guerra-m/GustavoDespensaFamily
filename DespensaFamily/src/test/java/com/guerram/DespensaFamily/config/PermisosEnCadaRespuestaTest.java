package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import com.guerram.DespensaFamily.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * El menu del frontend se armaba con los permisos que quedaron en
 * localStorage al loguear. Desde DF-SEC-002 el servidor los lee de la base en
 * cada pedido, asi que si el dueño le cambiaba un permiso a alguien con la
 * sesion abierta, el servidor aplicaba el cambio y el menu no se enteraba.
 *
 * <p>Cada respuesta a un pedido con sesion valida trae en {@code X-Permisos}
 * el rol y los permisos que el servidor acaba de usar para decidir. El
 * frontend lo lee en el mismo fetch que ya hace cada pantalla: sin pedido
 * extra por navegacion.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class PermisosEnCadaRespuestaTest {

    private static final String DUENIO = "perm-hdr-duenio";
    private static final String EMPLEADO = "perm-hdr-empleado";
    private static final String PASSWORD = "una-contrasena-larga-de-prueba";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void crear() {
        borrar();
        guardar(DUENIO, "ADMIN");
        guardar(EMPLEADO, "EMPLEADO");
    }

    @AfterEach
    void borrar() {
        for (String u : List.of(DUENIO, EMPLEADO)) {
            usuarioRepository.findByUsername(u).ifPresent(usuarioRepository::delete);
            usuarioService.olvidarIntentos(u, "127.0.0.1");
        }
    }

    private void guardar(String username, String rol) {
        usuarioRepository.save(Usuario.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .nombre(username)
                .rol(rol)
                .build());
    }

    private Cookie loguear(String username) throws Exception {
        return mockMvc.perform(post("/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
    }

    private JsonNode permisos(MockHttpServletResponse r) throws Exception {
        String cabecera = r.getHeader("X-Permisos");
        assertThat(cabecera).as("cabecera X-Permisos").isNotNull();
        return JSON.readTree(cabecera);
    }

    @Test
    void cadaRespuestaDiceElRolYLosPermisosQueUsoElServidor() throws Exception {
        JsonNode empleado = permisos(mockMvc.perform(get("/configuracion").cookie(loguear(EMPLEADO)))
                .andReturn().getResponse());
        assertThat(empleado.get("rol").asString()).isEqualTo("EMPLEADO");
        assertThat(empleado.get("puedeGestionarProductos").asBoolean()).isFalse();
        assertThat(empleado.get("puedeVerCaja").asBoolean()).isFalse();

        JsonNode duenio = permisos(mockMvc.perform(get("/configuracion").cookie(loguear(DUENIO)))
                .andReturn().getResponse());
        assertThat(duenio.get("rol").asString()).isEqualTo("ADMIN");
    }

    @Test
    void unPermisoCambiadoConLaSesionAbiertaLlegaEnElProximoPedido() throws Exception {
        Cookie cookie = loguear(EMPLEADO);

        Usuario u = usuarioRepository.findByUsername(EMPLEADO).orElseThrow();
        u.setPuedeVerCaja(true);
        u.setPuedeGestionarProductos(true);
        usuarioRepository.save(u);

        JsonNode ahora = permisos(mockMvc.perform(get("/ventas").cookie(cookie)).andReturn().getResponse());
        assertThat(ahora.get("puedeVerCaja").asBoolean()).isTrue();
        assertThat(ahora.get("puedeGestionarProductos").asBoolean()).isTrue();
    }

    @Test
    void tambienViajaEnUn403ParaQueElMenuSeCorrijaAunqueElPedidoRebote() throws Exception {
        Cookie cookie = loguear(EMPLEADO);

        MockHttpServletResponse r = mockMvc.perform(get("/caja").cookie(cookie)).andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(permisos(r).get("puedeVerCaja").asBoolean()).isFalse();
    }

    @Test
    void sinSesionValidaNoHayCabecera() throws Exception {
        assertThat(mockMvc.perform(get("/productos")).andReturn().getResponse().getHeader("X-Permisos"))
                .isNull();
        assertThat(mockMvc.perform(get("/productos").cookie(new Cookie(CookieDeSesion.NOMBRE, "roto")))
                .andReturn().getResponse().getHeader("X-Permisos")).isNull();
    }

    @Test
    void enDesarrolloElNavegadorPuedeLeerlaDesdeElOrigenDeVite() throws Exception {
        // Front en 5173 y back en otro puerto: sin Expose-Headers el
        // navegador esconde la cabecera al script aunque llegue.
        MockHttpServletResponse r = mockMvc.perform(get("/configuracion")
                        .cookie(loguear(EMPLEADO))
                        .header("Origin", "http://localhost:5173"))
                .andReturn().getResponse();

        assertThat(r.getHeader("Access-Control-Expose-Headers")).contains("X-Permisos");
    }
}
