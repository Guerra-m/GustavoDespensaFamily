package com.guerram.DespensaFamily.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * La sesion viaja en una cookie HttpOnly y no en localStorage.
 *
 * <p><b>El bug que lo motivo:</b> la auditoria del 01/09/2026 dejo
 * {@code /imagenes/**} como {@code authenticated()}, con buen criterio — antes
 * cualquiera en el wifi del local podia recorrer el catalogo de fotos sin
 * loguearse. Pero el token vivia en localStorage, y una etiqueta
 * {@code <img src="/imagenes/foto.jpg">} <b>no puede mandar una cabecera
 * Authorization</b>. Resultado: desde el 01/09 no se veia ninguna foto de
 * producto, ni en Productos ni en la pantalla de Ventas.
 *
 * <p>La cookie la manda el navegador sola en cada pedido, incluidos los de las
 * imagenes. Y de paso cierra lo que la propia auditoria habia marcado: con el
 * token en localStorage, cualquier XSS era robo de sesion directo (FE-02).
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class SesionEnCookieTest {

    private static final String USUARIO = "duenio-de-prueba";
    private static final String PASSWORD = "una-contrasena-larga-de-prueba";

    @Autowired
    private MockMvc mockMvc;

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
                .nombre("Duenio de prueba")
                .rol("ADMIN")
                .build());
    }

    private MvcResult loguear() throws Exception {
        return mockMvc.perform(post("/usuario/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + USUARIO + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
    }

    private Cookie cookieDeSesion() throws Exception {
        return loguear().getResponse().getCookie(CookieDeSesion.NOMBRE);
    }

    @Test
    void elLoginDejaLaSesionEnUnaCookieHttpOnly() throws Exception {
        Cookie cookie = cookieDeSesion();

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        // HttpOnly es todo el punto: ningun script de la pagina puede leerla,
        // asi que un XSS ya no se lleva la sesion.
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    void elLoginYaNoDevuelveElTokenEnElCuerpo() throws Exception {
        String cuerpo = loguear().getResponse().getContentAsString();

        // Si siguiera viniendo en el JSON, el frontend podria volver a
        // guardarlo en localStorage sin querer y tendriamos las dos cosas a la
        // vez, que es el peor de los mundos (FE-02: "mezclar estrategias").
        assertThat(cuerpo).doesNotContain("token");
        // Lo que si necesita la pantalla para decidir que mostrar.
        assertThat(cuerpo).contains("\"rol\":\"ADMIN\"");
    }

    @Test
    void conLaCookieSeVenLasFotosDeLosProductos() throws Exception {
        // El caso que estaba roto. Una etiqueta <img> no manda cabeceras, pero
        // si manda las cookies del dominio.
        int sinCookie = mockMvc.perform(get("/imagenes/cualquiera.jpg"))
                .andReturn().getResponse().getStatus();
        int conCookie = mockMvc.perform(get("/imagenes/cualquiera.jpg").cookie(cookieDeSesion()))
                .andReturn().getResponse().getStatus();

        assertThat(sinCookie).isEqualTo(403);
        // 404 y no 403: paso el control de acceso y murio buscando un archivo
        // que no existe, que es lo que se queria comprobar.
        assertThat(conCookie).isNotEqualTo(403);
    }

    @Test
    void laCookieAlcanzaParaLaApi() throws Exception {
        int estado = mockMvc.perform(get("/productos").cookie(cookieDeSesion()))
                .andReturn().getResponse().getStatus();

        assertThat(estado).isEqualTo(200);
    }

    @Test
    void unaCookieInventadaNoEntra() throws Exception {
        int estado = mockMvc.perform(
                get("/productos").cookie(new Cookie(CookieDeSesion.NOMBRE, "esto-no-es-un-jwt")))
                .andReturn().getResponse().getStatus();

        assertThat(estado).isIn(401, 403);
    }

    @Test
    void cerrarSesionBorraLaCookie() throws Exception {
        MvcResult salida = mockMvc.perform(
                post("/usuario/logout").cookie(cookieDeSesion())
                        .header("Origin", "http://localhost"))
                .andReturn();

        Cookie borrada = salida.getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(borrada).isNotNull();
        // Maxage 0 es como se borra una cookie: el navegador la tira. Cerrar
        // sesion tiene que apagarla del lado del servidor y no solo del cliente.
        assertThat(borrada.getMaxAge()).isZero();
        assertThat(borrada.getValue()).isEmpty();
    }

    // ------------------------------------------------------------------ CSRF

    @Test
    void unSitioAjenoNoPuedeUsarLaCookieParaEscribir() throws Exception {
        // El costo de pasar a cookies (SESS-10): el navegador la manda sola,
        // asi que una pagina cualquiera podria disparar un pedido con la sesion
        // del duenio puesta. SameSite=Strict lo frena en el navegador; esta
        // comprobacion de Origin lo frena tambien en el servidor.
        int estado = mockMvc.perform(post("/productos")
                .cookie(cookieDeSesion())
                .header("Origin", "http://sitio-malicioso.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(estado).isEqualTo(403);
    }

    @Test
    void elPropioSitioSiPuedeEscribir() throws Exception {
        int estado = mockMvc.perform(post("/productos")
                .cookie(cookieDeSesion())
                .header("Origin", "http://localhost")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andReturn().getResponse().getStatus();

        // Pasa el control de origen; que despues falle por el payload vacio es
        // otro asunto y justamente lo que se quiere ver.
        assertThat(estado).isNotEqualTo(403);
    }

    @Test
    void unSitioAjenoTampocoPuedeLeer() throws Exception {
        // FiltroDeOrigen deja pasar los GET a proposito, pero eso no significa
        // que una pagina cualquiera pueda leer datos con la cookie del duenio:
        // antes que el filtro esta CORS, y el origen no figura en la lista.
        //
        // La distincion importa: los pedidos del propio frontend son
        // same-origin y no llevan cabecera Origin, asi que CORS ni los mira.
        // El unico que la lleva es el pedido que viene de otro sitio.
        int estado = mockMvc.perform(get("/productos")
                .cookie(cookieDeSesion())
                .header("Origin", "http://sitio-cualquiera.example"))
                .andReturn().getResponse().getStatus();

        assertThat(estado).isEqualTo(403);
    }

    @Test
    void unClienteSinOrigenSigueFuncionando() throws Exception {
        // curl, el instalador, un script: no mandan Origin. Solo se rechaza un
        // Origin presente y ajeno, que es lo que manda siempre un navegador
        // cuando el pedido viene de otro sitio.
        int estado = mockMvc.perform(post("/productos")
                .cookie(cookieDeSesion())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(estado).isNotEqualTo(403);
    }
}
