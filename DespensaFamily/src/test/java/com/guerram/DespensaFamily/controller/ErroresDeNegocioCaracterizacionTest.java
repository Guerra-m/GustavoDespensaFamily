package com.guerram.DespensaFamily.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guerram.DespensaFamily.model.EstadoVenta;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.Venta;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.VentaRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * DF-DEBT-003: fija el comportamiento HTTP actual (codigo + cuerpo) de los
 * errores de negocio "clave" ANTES de migrar de RuntimeException generica a
 * excepciones tipadas en GlobalExceptionHandler. Estos tests tienen que
 * seguir pasando exactamente igual despues de la migracion — lo unico que
 * puede cambiar es COMO se llega al mismo status/cuerpo, nunca el status ni
 * el cuerpo en si (el frontend ya depende de esto).
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class ErroresDeNegocioCaracterizacionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private VentaRepository ventaRepository;

    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");
    private static final RequestPostProcessor EMPLEADO = user("empleado").roles("EMPLEADO");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void ventaSinStockSuficienteDevuelve400ConElMensajeDeSiempre() throws Exception {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Stock insuficiente " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(5)).precioVenta(BigDecimal.valueOf(10)).porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(1)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        String body = JSON.writeValueAsString(Map.of(
                "detalles", List.of(Map.of("productoId", producto.getId(), "cantidad", 5)),
                "metodoPago", "EFECTIVO"));

        var resultado = mockMvc.perform(post("/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(EMPLEADO))
                .andReturn();

        assertEquals(400, resultado.getResponse().getStatus());
        var json = JSON.readTree(resultado.getResponse().getContentAsString());
        assertEquals("Stock insuficiente", json.get("error").asText());
    }

    @Test
    void venderUnProductoInexistenteDevuelve404ConElMensajeDeSiempre() throws Exception {
        String body = JSON.writeValueAsString(Map.of(
                "detalles", List.of(Map.of("productoId", 999999999L, "cantidad", 1)),
                "metodoPago", "EFECTIVO"));

        var resultado = mockMvc.perform(post("/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(EMPLEADO))
                .andReturn();

        assertEquals(404, resultado.getResponse().getStatus());
        var json = JSON.readTree(resultado.getResponse().getContentAsString());
        assertEquals("Producto no encontrado", json.get("error").asText());
    }

    // DF-SEC-003: una venta ajena (de otro usuario) tiene que dar el MISMO
    // 404 "Venta no encontrada" que una que directamente no existe — nunca
    // un 403, que confirmaria que el numero de venta existe.
    @Test
    void unEmpleadoQuePideUnaVentaAjenaRecibeElMismo404QueUnaInexistente() throws Exception {
        // Fecha de ayer, no de hoy: esta venta es solo un fixture para
        // probar el 404 por "no es mia", no una venta real del dia — otros
        // tests de la suite (GananciaConPromoTest) miden totales de HOY
        // sobre la misma base compartida, y una venta de $10 sin detalles
        // fechada hoy les corre el numero.
        Venta ventaDeOtro = ventaRepository.save(Venta.builder()
                .fechaHora(LocalDateTime.now().minusDays(1))
                .estado(EstadoVenta.ACTIVA)
                .total(BigDecimal.TEN)
                .usuario("otro-empleado")
                .build());

        var resultado = mockMvc.perform(get("/ventas/" + ventaDeOtro.getId())
                        .with(EMPLEADO))
                .andReturn();

        assertEquals(404, resultado.getResponse().getStatus());
        var json = JSON.readTree(resultado.getResponse().getContentAsString());
        assertEquals("Venta no encontrada", json.get("error").asText());
    }

    @Test
    void anularUnaVentaYaAnuladaDevuelve400ConElMensajeDeSiempre() throws Exception {
        // Idem: fecha de ayer para no ensuciar los totales de "hoy" que
        // miden otros tests contra la misma base compartida.
        Venta venta = ventaRepository.save(Venta.builder()
                .fechaHora(LocalDateTime.now().minusDays(1))
                .estado(EstadoVenta.ANULADA)
                .total(BigDecimal.TEN)
                .usuario("admin")
                .build());

        var resultado = mockMvc.perform(delete("/ventas/" + venta.getId())
                        .with(ADMIN))
                .andReturn();

        assertEquals(400, resultado.getResponse().getStatus());
        var json = JSON.readTree(resultado.getResponse().getContentAsString());
        assertEquals("La venta ya está anulada", json.get("error").asText());
    }

    @Test
    void crearUnSegundoDescuentoActivoParaElMismoProductoDevuelve400ConElMensajeDeSiempre() throws Exception {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Descuento duplicado " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(100)).precioVenta(BigDecimal.valueOf(200)).porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        String primero = JSON.writeValueAsString(Map.of(
                "alcance", "PRODUCTO",
                "productoId", producto.getId(),
                "porcentaje", 10,
                "activo", true));
        mockMvc.perform(post("/descuentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(primero)
                        .with(ADMIN))
                .andReturn();

        String segundo = JSON.writeValueAsString(Map.of(
                "alcance", "PRODUCTO",
                "productoId", producto.getId(),
                "porcentaje", 20,
                "activo", true));
        var resultado = mockMvc.perform(post("/descuentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(segundo)
                        .with(ADMIN))
                .andReturn();

        assertEquals(400, resultado.getResponse().getStatus());
        var json = JSON.readTree(resultado.getResponse().getContentAsString());
        String mensaje = json.get("error").asText();
        assertTrue(mensaje.contains(producto.getNombre()), "tendria que nombrar el producto: " + mensaje);
        assertTrue(mensaje.contains("10"), "tendria que mencionar el porcentaje ya activo: " + mensaje);
    }
}
