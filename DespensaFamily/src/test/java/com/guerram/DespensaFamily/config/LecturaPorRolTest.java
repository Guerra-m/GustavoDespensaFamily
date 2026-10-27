package com.guerram.DespensaFamily.config;

import java.math.BigDecimal;

import com.guerram.DespensaFamily.model.Compra;
import com.guerram.DespensaFamily.model.DetalleVenta;
import com.guerram.DespensaFamily.model.EstadoVenta;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.model.Venta;
import com.guerram.DespensaFamily.repository.CompraRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import com.guerram.DespensaFamily.repository.VentaRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DF-SEC-003: que puede LEER cada uno. "Sin PERM_CAJA no ve la plata" y
 * "sin PERM_STOCK no ve los costos".
 *
 * <p>Antes cualquier autenticado leia todas las ventas con sus totales, los
 * costos y proveedores de cada producto (GET /productos) y todas las compras.
 * Decision de Martin: el empleado ve solo SUS ventas DEL DIA y los productos
 * sin costo.
 *
 * <p>Como SesionRevocableTest, todo pasa por el filtro JWT real con la cookie
 * que devuelve el login: {@code user(...)} arma la sesion por fuera del filtro
 * y no traeria los permisos de la base.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class LecturaPorRolTest {

    private static final String DUENIO = "lect-duenio";
    private static final String EMPLEADO = "lect-empleado";
    private static final String STOCK = "lect-stock";
    private static final String CAJA = "lect-caja";
    private static final String AMBOS = "lect-ambos";
    private static final String OTRO = "lect-otro";
    private static final List<String> TODOS = List.of(DUENIO, EMPLEADO, STOCK, CAJA, AMBOS, OTRO);
    private static final String PASSWORD = "una-contrasena-larga-de-prueba";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private CompraRepository compraRepository;

    private Producto producto;
    private Venta suyaDeHoy;
    private Venta suyaDeAyer;
    private Venta ajenaDeHoy;
    private Venta deStockDeHoy;
    private Compra compra;
    private final List<Venta> ventasCreadas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        limpiar();
        guardar(DUENIO, "ADMIN", false, false);
        guardar(EMPLEADO, "EMPLEADO", false, false);
        guardar(STOCK, "EMPLEADO", true, false);
        guardar(CAJA, "EMPLEADO", false, true);
        guardar(AMBOS, "EMPLEADO", true, true);
        guardar(OTRO, "EMPLEADO", false, false);

        producto = productoRepository.save(Producto.builder()
                .nombre("Yerba lectura-por-rol")
                .precioVenta(BigDecimal.valueOf(1500))
                .precioCosto(BigDecimal.valueOf(900))
                .porcentajeGanancia(BigDecimal.valueOf(66.6))
                .proveedor("Distribuidora Secreta")
                .stock(BigDecimal.valueOf(10))
                .tipoCantidad("UNIDAD")
                .unidadMedida("u")
                .fraccionMinima(BigDecimal.valueOf(1.0))
                .activo(true)
                .build());

        LocalDateTime ahora = LocalDateTime.now();
        suyaDeHoy = venta(EMPLEADO, ahora);
        suyaDeAyer = venta(EMPLEADO, ahora.minusDays(1));
        ajenaDeHoy = venta(OTRO, ahora);
        deStockDeHoy = venta(STOCK, ahora);

        compra = compraRepository.save(Compra.builder()
                .fechaHora(ahora)
                .proveedor("Distribuidora Secreta")
                .total(BigDecimal.valueOf(9000))
                .metodoPago(MetodoPago.EFECTIVO)
                .pagada(true)
                .usuario(DUENIO)
                .detalles(new ArrayList<>())
                .build());
    }

    @AfterEach
    void limpiar() {
        ventasCreadas.forEach(v -> ventaRepository.findById(v.getId()).ifPresent(ventaRepository::delete));
        ventasCreadas.clear();
        if (compra != null) {
            compraRepository.findById(compra.getId()).ifPresent(compraRepository::delete);
            compra = null;
        }
        if (producto != null) {
            productoRepository.findById(producto.getId()).ifPresent(productoRepository::delete);
            producto = null;
        }
        for (String u : TODOS) {
            usuarioRepository.findByUsername(u).ifPresent(usuarioRepository::delete);
            usuarioService.olvidarIntentos(u, "127.0.0.1");
        }
    }

    // ------------------------------------------------------------- armado

    private void guardar(String username, String rol, boolean stock, boolean caja) {
        usuarioRepository.save(Usuario.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .nombre(username)
                .rol(rol)
                .puedeGestionarProductos(stock)
                .puedeVerCaja(caja)
                .build());
    }

    private Venta venta(String usuario, LocalDateTime cuando) {
        Venta v = Venta.builder()
                .fechaHora(cuando)
                .total(BigDecimal.valueOf(1500))
                .metodoPago(MetodoPago.EFECTIVO)
                .usuario(usuario)
                .estado(EstadoVenta.ACTIVA)
                .build();
        v.setDetalles(new ArrayList<>(List.of(DetalleVenta.builder()
                .venta(v)
                .producto(producto)
                .cantidad(BigDecimal.valueOf(1))
                .precioUnitario(BigDecimal.valueOf(1500))
                .precioCosto(BigDecimal.valueOf(900))
                .subtotal(BigDecimal.valueOf(1500))
                .build())));
        Venta guardada = ventaRepository.save(v);
        ventasCreadas.add(guardada);
        return guardada;
    }

    private Cookie loguear(String username) throws Exception {
        Cookie cookie = mockMvc.perform(post("/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(cookie).as("el login de " + username + " tendria que dar cookie").isNotNull();
        return cookie;
    }

    private MockHttpServletResponse pedir(String username, MockHttpServletRequestBuilder pedido) throws Exception {
        return mockMvc.perform(pedido.cookie(loguear(username))).andReturn().getResponse();
    }

    private int estado(String username, MockHttpServletRequestBuilder pedido) throws Exception {
        return pedir(username, pedido).getStatus();
    }

    private JsonNode json(String username, String url) throws Exception {
        MockHttpServletResponse r = pedir(username, get(url));
        assertThat(r.getStatus()).as("GET " + url + " como " + username).isEqualTo(200);
        return JSON.readTree(r.getContentAsString());
    }

    private JsonNode nuestroProducto(JsonNode lista) {
        for (JsonNode p : lista) {
            if (p.get("id").asLong() == producto.getId()) return p;
        }
        throw new AssertionError("el producto de prueba no vino en la lista");
    }

    private List<Long> ids(JsonNode lista) {
        List<Long> ids = new ArrayList<>();
        lista.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private static void sinCostos(JsonNode p) {
        // Que el campo NO venga, no que venga en null o en 0.
        assertThat(p.has("precioCosto")).as("precioCosto en " + p).isFalse();
        assertThat(p.has("porcentajeGanancia")).as("porcentajeGanancia en " + p).isFalse();
        assertThat(p.has("proveedor")).as("proveedor en " + p).isFalse();
        // Lo que hace falta para vender sigue estando.
        assertThat(p.get("precioVenta").asDouble()).isEqualTo(1500);
        assertThat(p.get("nombre").asString()).isEqualTo("Yerba lectura-por-rol");
        assertThat(p.get("stock").asDouble()).isEqualTo(10);
    }

    private static void conCostos(JsonNode p) {
        assertThat(p.get("precioCosto").asDouble()).isEqualTo(900);
        assertThat(p.get("porcentajeGanancia").asDouble()).isEqualTo(66.6);
        assertThat(p.get("proveedor").asString()).isEqualTo("Distribuidora Secreta");
    }

    // ------------------------------------------------------------ productos

    @Test
    void elEmpleadoSinPermisosVeLosProductosSinCostoNiProveedor() throws Exception {
        sinCostos(nuestroProducto(json(EMPLEADO, "/productos")));
        sinCostos(json(EMPLEADO, "/productos/" + producto.getId()));
        sinCostos(nuestroProducto(json(EMPLEADO, "/productos/buscar?nombre=lectura-por-rol")));
    }

    @Test
    void elCajeroSinPermStockTampocoVeCostos() throws Exception {
        // PERM_CAJA es ver la plata del negocio, no los costos del catalogo.
        sinCostos(nuestroProducto(json(CAJA, "/productos")));
        sinCostos(json(CAJA, "/productos/" + producto.getId()));
    }

    @Test
    void conPermStockOSiendoDuenioSeVenLosCostos() throws Exception {
        conCostos(nuestroProducto(json(STOCK, "/productos")));
        conCostos(json(STOCK, "/productos/" + producto.getId()));
        conCostos(nuestroProducto(json(AMBOS, "/productos/buscar?nombre=lectura-por-rol")));
        conCostos(nuestroProducto(json(DUENIO, "/productos")));
    }

    // --------------------------------------------------------------- ventas

    @Test
    void elEmpleadoSinPermCajaVeSoloSusVentasDeHoy() throws Exception {
        List<Long> vistas = ids(json(EMPLEADO, "/ventas"));

        assertThat(vistas).contains(suyaDeHoy.getId())
                .doesNotContain(suyaDeAyer.getId(), ajenaDeHoy.getId(), deStockDeHoy.getId());
    }

    @Test
    void permStockNoAlcanzaParaVerLasVentasDeOtros() throws Exception {
        List<Long> vistas = ids(json(STOCK, "/ventas"));

        assertThat(vistas).contains(deStockDeHoy.getId())
                .doesNotContain(suyaDeHoy.getId(), ajenaDeHoy.getId(), suyaDeAyer.getId());
    }

    // DF-PERF-001 + DF-SEC-003: /ventas/pagina tiene que aplicar EXACTAMENTE
    // la misma regla que GET /ventas, no una lista aparte que alguien se
    // olvide de filtrar.
    @Test
    void elEmpleadoSinPermCajaVeSoloSusVentasDeHoyPaginado() throws Exception {
        JsonNode pagina = json(EMPLEADO, "/ventas/pagina?size=50");
        List<Long> vistas = ids(pagina.get("contenido"));

        assertThat(vistas).contains(suyaDeHoy.getId())
                .doesNotContain(suyaDeAyer.getId(), ajenaDeHoy.getId(), deStockDeHoy.getId());
    }

    @Test
    void elEmpleadoSinPermCajaNoPuedePedirOtroRangoDeFechasEnLaPagina() throws Exception {
        // Ignora desde/hasta a proposito: si no los ignorara, un empleado sin
        // PERM_CAJA podria pedir "todo el año pasado" y ver ventas ajenas.
        String desde = LocalDate.now().minusYears(5).atStartOfDay().toString();
        String hasta = LocalDate.now().plusYears(5).atStartOfDay().toString();

        JsonNode pagina = json(EMPLEADO, "/ventas/pagina?size=50&desde=" + desde + "&hasta=" + hasta);
        List<Long> vistas = ids(pagina.get("contenido"));

        assertThat(vistas).contains(suyaDeHoy.getId())
                .doesNotContain(suyaDeAyer.getId(), ajenaDeHoy.getId(), deStockDeHoy.getId());
    }

    @Test
    void conPermCajaOSiendoDuenioSeVenTodasLasVentasPaginado() throws Exception {
        for (String quien : List.of(CAJA, AMBOS, DUENIO)) {
            List<Long> vistas = ids(json(quien, "/ventas/pagina?size=50").get("contenido"));
            assertThat(vistas).as("ventas paginadas que ve " + quien)
                    .contains(suyaDeHoy.getId(), suyaDeAyer.getId(), ajenaDeHoy.getId(), deStockDeHoy.getId());
        }
    }

    @Test
    void conPermCajaOSiendoDuenioSeVenTodasLasVentas() throws Exception {
        for (String quien : List.of(CAJA, AMBOS, DUENIO)) {
            assertThat(ids(json(quien, "/ventas")))
                    .as("ventas que ve " + quien)
                    .contains(suyaDeHoy.getId(), suyaDeAyer.getId(), ajenaDeHoy.getId(), deStockDeHoy.getId());
        }
    }

    @Test
    void unaVentaAjenaODeOtroDiaDaElMismo404QueUnaQueNoExiste() throws Exception {
        MockHttpServletResponse inexistente = pedir(EMPLEADO, get("/ventas/999999999/detalle"));
        assertThat(inexistente.getStatus()).isEqualTo(404);

        for (Venta ajena : List.of(ajenaDeHoy, suyaDeAyer)) {
            for (String url : List.of("/ventas/" + ajena.getId(), "/ventas/" + ajena.getId() + "/detalle")) {
                MockHttpServletResponse r = pedir(EMPLEADO, get(url));
                assertThat(r.getStatus()).as(url).isEqualTo(404);
                // Mismo cuerpo: la respuesta no distingue "existe pero no es
                // tuya" de "no existe" (AUTZ-02).
                assertThat(r.getContentAsString()).as(url).isEqualTo(inexistente.getContentAsString());
            }
        }
    }

    @Test
    void elEmpleadoVeElTicketDeSuVentaDeHoySinElCosto() throws Exception {
        JsonNode resumen = json(EMPLEADO, "/ventas/" + suyaDeHoy.getId());
        assertThat(resumen.get("total").asDouble()).isEqualTo(1500);

        JsonNode detalle = json(EMPLEADO, "/ventas/" + suyaDeHoy.getId() + "/detalle");
        assertThat(detalle.get("total").asDouble()).isEqualTo(1500);
        JsonNode linea = detalle.get("detalles").get(0);
        assertThat(linea.get("precioUnitario").asDouble()).isEqualTo(1500);
        assertThat(linea.has("precioCosto")).as("precioCosto en " + linea).isFalse();
    }

    @Test
    void elCostoDeLaLineaDeVentaSoloLoVeQuienVeCostos() throws Exception {
        String url = "/ventas/" + ajenaDeHoy.getId() + "/detalle";

        JsonNode delCajero = json(CAJA, url).get("detalles").get(0);
        assertThat(delCajero.has("precioCosto")).as("cajero sin PERM_STOCK").isFalse();

        assertThat(json(AMBOS, url).get("detalles").get(0).get("precioCosto").asDouble()).isEqualTo(900);
        assertThat(json(DUENIO, url).get("detalles").get(0).get("precioCosto").asDouble()).isEqualTo(900);
    }

    @Test
    void anularSigueSiendoSoloDelDuenio() throws Exception {
        for (String quien : List.of(EMPLEADO, STOCK, CAJA, AMBOS)) {
            assertThat(estado(quien, delete("/ventas/" + suyaDeHoy.getId()))).as(quien).isEqualTo(403);
        }
        assertThat(ventaRepository.findById(suyaDeHoy.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVenta.ACTIVA);
    }

    // -------------------------------------------------------------- compras

    @Test
    void sinPermStockNoSeVenLasCompras() throws Exception {
        for (String quien : List.of(EMPLEADO, CAJA)) {
            assertThat(estado(quien, get("/compras"))).as(quien).isEqualTo(403);
            assertThat(estado(quien, get("/compras/" + compra.getId()))).as(quien).isEqualTo(403);
            assertThat(estado(quien, get("/compras/" + compra.getId() + "/detalle"))).as(quien).isEqualTo(403);
        }
    }

    @Test
    void conPermStockOSiendoDuenioSeVenLasCompras() throws Exception {
        for (String quien : List.of(STOCK, AMBOS, DUENIO)) {
            assertThat(ids(json(quien, "/compras"))).as(quien).contains(compra.getId());
            assertThat(estado(quien, get("/compras/" + compra.getId() + "/detalle"))).as(quien).isEqualTo(200);
        }
    }

    // DF-PERF-001: /compras/pagina cae bajo el mismo requestMatcher que el
    // resto de /compras/** en SecurityConfig (GET exige ROLE_ADMIN o
    // PERM_STOCK) -- estos dos tests confirman que el endpoint nuevo no
    // quedo afuera de ese filtro por accidente.
    @Test
    void sinPermStockNoSeVeLaPaginaDeCompras() throws Exception {
        for (String quien : List.of(EMPLEADO, CAJA)) {
            assertThat(estado(quien, get("/compras/pagina"))).as(quien).isEqualTo(403);
        }
    }

    @Test
    void conPermStockOSiendoDuenioSeVeLaPaginaDeCompras() throws Exception {
        for (String quien : List.of(STOCK, AMBOS, DUENIO)) {
            JsonNode pagina = json(quien, "/compras/pagina?size=50");
            assertThat(ids(pagina.get("contenido"))).as(quien).contains(compra.getId());
        }
    }

    // --------------------------------------------------------- configuracion

    @Test
    void laGananciaPorDefectoSoloLaVeQuienVeCostos() throws Exception {
        for (String quien : List.of(EMPLEADO, CAJA)) {
            JsonNode config = json(quien, "/configuracion");
            assertThat(config.has("gananciaPorDefecto")).as(quien).isFalse();
            assertThat(config.has("nombreNegocio")).as(quien).isTrue();
        }
        for (String quien : List.of(STOCK, DUENIO)) {
            assertThat(json(quien, "/configuracion").has("gananciaPorDefecto")).as(quien).isTrue();
        }
    }
}
