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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DF-SEC-002 (SESS-06): borrar un usuario o cambiarle los permisos tiene
 * efecto en el proximo pedido, no cuando vence el token.
 *
 * <p>Antes JwtAuthFilter armaba rol y permisos con lo que decia el token, sin
 * mirar la base. Reproducido: una venta hecha con la cookie de un usuario ya
 * borrado; la caja visible despues de quitarle PERM_CAJA; y al reves, un
 * permiso recien dado no servia hasta volver a entrar. El token vive 24 h.
 *
 * <p>Estos tests pasan por el filtro JWT de verdad —loguean y usan la cookie
 * que devuelve el login— y no por {@code user(...)}, que arma la sesion por
 * fuera del filtro y no probaria nada de esto.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class SesionRevocableTest {

    private static final String DUENIO = "duenio-revoca";
    private static final String EMPLEADO = "empleado-revocable";
    private static final String OTRO = "otro-empleado-revoca";
    private static final String PASSWORD = "una-contrasena-larga-de-prueba";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void crearUsuarios() {
        borrarUsuarios();
        guardar(DUENIO, "ADMIN", false, false);
        guardar(EMPLEADO, "EMPLEADO", false, false);
        guardar(OTRO, "EMPLEADO", false, false);
    }

    @AfterEach
    void borrarUsuarios() {
        for (String u : List.of(DUENIO, EMPLEADO, OTRO)) {
            usuarioRepository.findByUsername(u).ifPresent(usuarioRepository::delete);
            usuarioService.olvidarIntentos(u, "127.0.0.1");
        }
    }

    private Usuario guardar(String username, String rol, boolean stock, boolean caja) {
        return usuarioRepository.save(Usuario.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .nombre(username)
                .rol(rol)
                .puedeGestionarProductos(stock)
                .puedeVerCaja(caja)
                .build());
    }

    private Usuario buscar(String username) {
        return usuarioRepository.findByUsername(username).orElseThrow();
    }

    private Cookie loguear(String username) throws Exception {
        Cookie cookie = mockMvc.perform(post("/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(cookie).as("el login de " + username + " tendria que dar cookie").isNotNull();
        return cookie;
    }

    private int estado(MockHttpServletRequestBuilder pedido, Cookie cookie) throws Exception {
        return mockMvc.perform(pedido.cookie(cookie)).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder venta() {
        return post("/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"detalles\":[]}");
    }

    // ------------------------------------------------------- usuario borrado

    @Test
    void laCookieDeUnUsuarioBorradoDejaDeServirAlInstante() throws Exception {
        Cookie cookie = loguear(EMPLEADO);
        assertThat(estado(get("/productos"), cookie)).isEqualTo(200);

        usuarioRepository.delete(buscar(EMPLEADO));

        assertThat(estado(get("/productos"), cookie)).isEqualTo(401);
        // El caso reproducido en la auditoria: vender con la cookie de un
        // usuario borrado.
        assertThat(estado(venta(), cookie)).isEqualTo(401);
    }

    @Test
    void borrarloDesdeLaPantallaDeUsuariosTambienLeCortaElAcceso() throws Exception {
        Cookie delDuenio = loguear(DUENIO);
        Cookie delEmpleado = loguear(EMPLEADO);

        int borrado = estado(delete("/admin/usuarios/" + buscar(EMPLEADO).getId()), delDuenio);
        assertThat(borrado).isEqualTo(204);

        assertThat(estado(get("/ventas"), delEmpleado)).isEqualTo(401);
        // El dueño que lo borro sigue adentro.
        assertThat(estado(get("/admin/usuarios"), delDuenio)).isEqualTo(200);
    }

    @Test
    void el401DeUnaSesionRevocadaBorraLaCookie() throws Exception {
        Cookie cookie = loguear(EMPLEADO);
        usuarioRepository.delete(buscar(EMPLEADO));

        MvcResult r = mockMvc.perform(get("/productos").cookie(cookie)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        Cookie borrada = r.getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(borrada).isNotNull();
        assertThat(borrada.getMaxAge()).isZero();
    }

    // ------------------------------- lo publico sigue andando con esa cookie

    @Test
    void conLaCookieDeUnUsuarioBorradoSeSigueViendoElLoginYSePuedeEntrarConOtro() throws Exception {
        Cookie vieja = loguear(EMPLEADO);
        usuarioRepository.delete(buscar(EMPLEADO));

        // La pantalla de login y sus iconos: si esto diera 401, el navegador
        // que tiene la cookie vieja no podria ni mostrar el login.
        assertThat(estado(get("/"), vieja)).isNotIn(401, 403);
        assertThat(estado(get("/icons/users.svg"), vieja)).isNotIn(401, 403);

        // Otro usuario entra desde ese mismo navegador.
        MvcResult login = mockMvc.perform(post("/usuario/login").cookie(vieja)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + OTRO + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(login.getResponse().getCookie(CookieDeSesion.NOMBRE).getValue()).isNotBlank();
    }

    @Test
    void cerrarSesionConLaCookieDeUnUsuarioBorradoLaLimpia() throws Exception {
        Cookie vieja = loguear(EMPLEADO);
        usuarioRepository.delete(buscar(EMPLEADO));

        MvcResult salida = mockMvc.perform(post("/usuario/logout").cookie(vieja)).andReturn();

        assertThat(salida.getResponse().getStatus()).isEqualTo(200);
        assertThat(salida.getResponse().getCookie(CookieDeSesion.NOMBRE).getMaxAge()).isZero();
    }

    @Test
    void cerrarSesionConUnaCookieRotaTambienLaLimpia() throws Exception {
        // Lo que ya prometia el comentario de /usuario/logout y el filtro no
        // cumplia: con un token invalido respondia 401 antes de llegar.
        MvcResult salida = mockMvc.perform(post("/usuario/logout")
                .cookie(new Cookie(CookieDeSesion.NOMBRE, "esto-no-es-un-jwt"))).andReturn();

        assertThat(salida.getResponse().getStatus()).isEqualTo(200);
        assertThat(salida.getResponse().getCookie(CookieDeSesion.NOMBRE).getMaxAge()).isZero();
    }

    // --------------------------------------------------------------- permisos

    @Test
    void quitarPermCajaSeNotaEnElProximoPedido() throws Exception {
        Usuario u = buscar(EMPLEADO);
        u.setPuedeVerCaja(true);
        usuarioRepository.save(u);
        Cookie cookie = loguear(EMPLEADO);
        assertThat(estado(get("/caja"), cookie)).isEqualTo(200);

        Cookie delDuenio = loguear(DUENIO);
        int cambio = estado(patch("/admin/usuarios/" + u.getId() + "/permisos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puedeGestionarProductos\":false,\"puedeVerCaja\":false}"), delDuenio);
        assertThat(cambio).isEqualTo(200);

        assertThat(estado(get("/caja"), cookie)).isEqualTo(403);
    }

    @Test
    void darPermStockSirveSinVolverAEntrar() throws Exception {
        Cookie cookie = loguear(EMPLEADO);
        MockHttpServletRequestBuilder crearCategoria = post("/categorias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        assertThat(estado(crearCategoria, cookie)).isEqualTo(403);

        Usuario u = buscar(EMPLEADO);
        u.setPuedeGestionarProductos(true);
        usuarioRepository.save(u);

        // Pasa el control de acceso: lo que venga despues (400 por el body
        // vacio) ya no es de permisos.
        assertThat(estado(post("/categorias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"), cookie)).isNotIn(401, 403);
    }

    // -------------------------------------------------------------------- rol

    @Test
    void elDuenioSigueEntrandoATodoConSuCookie() throws Exception {
        Cookie cookie = loguear(DUENIO);

        assertThat(estado(get("/admin/usuarios"), cookie)).isEqualTo(200);
        assertThat(estado(get("/caja"), cookie)).isEqualTo(200);
        assertThat(estado(get("/reportes/productos-bajo-stock"), cookie)).isEqualTo(200);
    }

    @Test
    void elRolTambienSaleDeLaBase() throws Exception {
        Cookie cookie = loguear(DUENIO);
        assertThat(estado(get("/admin/usuarios"), cookie)).isEqualTo(200);

        Usuario u = buscar(DUENIO);
        u.setRol("EMPLEADO");
        usuarioRepository.save(u);

        assertThat(estado(get("/admin/usuarios"), cookie)).isEqualTo(403);
        // Sigue siendo alguien con sesion: vender puede.
        assertThat(estado(get("/ventas"), cookie)).isEqualTo(200);
    }

    @Test
    void elEmpleadoSinPermisosSigueSinVerLaCaja() throws Exception {
        Cookie cookie = loguear(EMPLEADO);

        assertThat(estado(get("/caja"), cookie)).isEqualTo(403);
        assertThat(estado(get("/admin/usuarios"), cookie)).isEqualTo(403);
        assertThat(estado(get("/ventas"), cookie)).isEqualTo(200);
    }

    @Test
    void sinCookieUnaRutaProtegidaSigueDando403() throws Exception {
        // Sin sesion alguna no cambia nada: 403, como antes. El 401 es solo
        // para "tenias una sesion y ya no vale", que es lo que el frontend
        // usa para mandar al login.
        assertThat(mockMvc.perform(get("/productos")).andReturn().getResponse().getStatus())
                .isEqualTo(403);
    }
}
