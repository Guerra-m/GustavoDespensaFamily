package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.MovimientoCajaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * DF-BUG-003 (parte backend): dos peticiones de "registrar venta" con la
 * MISMA Idempotency-Key no pueden generar dos ventas. Reproducido en vivo con
 * un doble clic real en "Confirmar Venta": ventas #197 y #198, mismo carrito,
 * mismo milisegundo.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class VentaIdempotenciaTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MovimientoCajaRepository movimientoCajaRepository;

    private Producto crearProducto(double stock) {
        return productoRepository.save(Producto.builder()
                .nombre("Idempotencia " + System.nanoTime())
                .precioCosto(500).precioVenta(1000).porcentajeGanancia(100)
                .stock(stock).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void laMismaClaveDosVecesGeneraUnaSolaVenta() {
        Producto producto = crearProducto(50);
        String clave = UUID.randomUUID().toString();

        VentaDTO primera = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(producto.getId(), 2.0)),
                MetodoPago.EFECTIVO, null, clave);

        long movimientosDespuesDeLaPrimera = movimientoCajaRepository.count();

        VentaDTO segunda = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(producto.getId(), 2.0)),
                MetodoPago.EFECTIVO, null, clave);

        assertThat(segunda.getId()).isEqualTo(primera.getId());
        assertThat(movimientoCajaRepository.count()).isEqualTo(movimientosDespuesDeLaPrimera);

        Producto releido = productoRepository.findById(producto.getId()).orElseThrow();
        // Stock descontado UNA sola vez (50 - 2 = 48), no dos.
        assertThat(releido.getStock()).isEqualTo(48.0);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void clavesDistintasGeneranDosVentas() {
        Producto producto = crearProducto(50);

        VentaDTO primera = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(producto.getId(), 1.0)),
                MetodoPago.EFECTIVO, null, UUID.randomUUID().toString());

        VentaDTO segunda = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(producto.getId(), 1.0)),
                MetodoPago.EFECTIVO, null, UUID.randomUUID().toString());

        assertThat(segunda.getId()).isNotEqualTo(primera.getId());

        Producto releido = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(releido.getStock()).isEqualTo(48.0);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void sinClaveFuncionaComoAntesYPuedeRepetirseSinLimite() {
        Producto producto = crearProducto(50);

        VentaDTO primera = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(producto.getId(), 1.0)));

        VentaDTO segunda = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(producto.getId(), 1.0)));

        assertThat(segunda.getId()).isNotEqualTo(primera.getId());
    }
}
