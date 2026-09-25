package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.model.EstadoVenta;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.VentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quien vendio, a que hora, y quien anulo.
 *
 * <p>Antes de la auditoria del 01/09/2026 no se guardaba nada de eso: la venta
 * tenia fecha sin hora (el campo se llamaba {@code fechaHora} pero era un
 * LocalDate), no registraba que usuario la hizo, y al anularla desaparecia del
 * listado sin dejar constancia de quien la anulo. Con dos o tres empleados
 * rotando, eso hace imposible reconstruir que paso en la caja.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class RastroDeVentasTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private VentaRepository ventaRepository;

    private Producto unProducto() {
        return productoRepository.save(Producto.builder()
                .nombre("Producto para rastro")
                .precioCosto(BigDecimal.valueOf(50)).precioVenta(BigDecimal.valueOf(100)).porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("UNIDAD").unidadMedida("u")
                .fraccionMinima(BigDecimal.valueOf(1.0)).activo(true)
                .build());
    }

    private VentaDTO venderUnaUnidadDe(Producto p) {
        return ventaService.registrarVenta(List.of(new DetalleVentaRequestDTO(p.getId(), BigDecimal.valueOf(1))));
    }

    @Test
    @WithMockUser(username = "maria")
    void laVentaGuardaQuienLaHizo() {
        VentaDTO venta = venderUnaUnidadDe(unProducto());

        assertEquals("maria", ventaRepository.findById(venta.getId()).orElseThrow().getUsuario());
    }

    @Test
    @WithMockUser(username = "maria")
    void laVentaGuardaLaHoraYNoSoloLaFecha() {
        VentaDTO venta = venderUnaUnidadDe(unProducto());

        LocalDateTime momento = ventaRepository.findById(venta.getId()).orElseThrow().getFechaHora();
        assertNotNull(momento);
        assertEquals(LocalDate.now(), momento.toLocalDate());
        // Sin hora, dos ventas del mismo dia son indistinguibles y el ticket
        // no puede decir a que hora se cobro.
        assertTrue(momento.getHour() > 0 || momento.getMinute() > 0 || momento.getSecond() > 0
                        || momento.getNano() > 0,
                "la venta se guardo a las 00:00:00 exactas: se perdio la hora");
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void anularGuardaQuienAnuloYCuando() {
        VentaDTO venta = venderUnaUnidadDe(unProducto());

        ventaService.anularVenta(venta.getId());

        var anulada = ventaRepository.findById(venta.getId()).orElseThrow();
        assertEquals(EstadoVenta.ANULADA, anulada.getEstado());
        assertEquals("duenio", anulada.getAnuladaPor());
        assertNotNull(anulada.getFechaAnulacion());
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void laVentaAnuladaSigueApareciendoEnElListado() {
        // Que desapareciera era justamente lo que hacia util el fraude: se
        // cobraba, se anulaba, y no quedaba ni el renglon.
        VentaDTO venta = venderUnaUnidadDe(unProducto());
        ventaService.anularVenta(venta.getId());

        boolean sigueEnLaLista = ventaService.listarVentas().stream()
                .anyMatch(v -> v.getId().equals(venta.getId()));

        assertTrue(sigueEnLaLista, "la venta anulada desaparecio del listado");
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void elListadoDistingueLasAnuladas() {
        VentaDTO venta = venderUnaUnidadDe(unProducto());
        ventaService.anularVenta(venta.getId());

        VentaDTO enLista = ventaService.listarVentas().stream()
                .filter(v -> v.getId().equals(venta.getId()))
                .findFirst().orElseThrow();

        assertEquals(EstadoVenta.ANULADA, enLista.getEstado(),
                "el listado tiene que decir cuales estan anuladas para poder marcarlas");
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void anularDosVecesNoDevuelveElStockDosVeces() {
        Producto p = unProducto();
        BigDecimal stockInicial = p.getStock();
        VentaDTO venta = venderUnaUnidadDe(p);

        ventaService.anularVenta(venta.getId());
        try {
            ventaService.anularVenta(venta.getId());
        } catch (RuntimeException esperado) {
            // ya estaba anulada
        }

        assertEquals(0, stockInicial.compareTo(productoRepository.findById(p.getId()).orElseThrow().getStock()));
    }
}
