package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.CompraDTO;
import com.guerram.DespensaFamily.dto.CompraRequestDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraRequestDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Deshacer una compra mal cargada.
 *
 * <p>Antes no se podia. Una compra suma stock y ademas <b>reescribe el precio
 * de costo y el de venta</b> del producto: un cero de mas al tipear el costo
 * dejaba el producto con un precio absurdo y no habia forma de volver atras
 * salvo editarlo a mano adivinando los valores viejos.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class AnularCompraTest {

    @Autowired
    private CompraService compraService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ProductoRepository productoRepository;

    private Producto productoConCosto(double costo, double stock) {
        return productoRepository.save(Producto.builder()
                .nombre("Arroz de prueba")
                .precioCosto(costo).precioVenta(costo * 2).porcentajeGanancia(100)
                .stock(stock).tipoCantidad("UNIDAD").unidadMedida("u")
                .fraccionMinima(1.0).activo(true)
                .build());
    }

    private CompraDTO comprar(Producto p, double cantidad, double costo) {
        CompraRequestDTO pedido = new CompraRequestDTO();
        pedido.setProveedor("Mayorista");
        pedido.setDetalles(List.of(new DetalleCompraRequestDTO(p.getId(), cantidad, costo)));
        pedido.setMetodoPago(com.guerram.DespensaFamily.model.MetodoPago.EFECTIVO);
        return compraService.registrarCompra(pedido);
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void anularDevuelveElStockQueSumoLaCompra() {
        Producto p = productoConCosto(700, 10);
        CompraDTO compra = comprar(p, 50, 700);
        assertEquals(60.0, productoRepository.findById(p.getId()).orElseThrow().getStock());

        compraService.anularCompra(compra.getId());

        assertEquals(10.0, productoRepository.findById(p.getId()).orElseThrow().getStock());
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void anularRestauraElPrecioDeCostoQueHabiaAntes() {
        // El caso que motiva todo: se tipea 7000 en vez de 700.
        Producto p = productoConCosto(700, 10);
        CompraDTO compra = comprar(p, 50, 7000);
        assertEquals(7000.0, productoRepository.findById(p.getId()).orElseThrow().getPrecioCosto());

        compraService.anularCompra(compra.getId());

        Producto despues = productoRepository.findById(p.getId()).orElseThrow();
        assertEquals(700.0, despues.getPrecioCosto(),
                "anular tiene que devolver el costo que el producto tenia antes de la compra");
        assertEquals(1400.0, despues.getPrecioVenta(), 0.01,
                "y recalcular el precio de venta sobre ese costo");
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void noSePuedeAnularDosVeces() {
        Producto p = productoConCosto(700, 10);
        CompraDTO compra = comprar(p, 5, 700);
        compraService.anularCompra(compra.getId());

        assertThrows(RuntimeException.class, () -> compraService.anularCompra(compra.getId()));
        assertEquals(10.0, productoRepository.findById(p.getId()).orElseThrow().getStock(),
                "la segunda anulacion no puede volver a restar el stock");
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void noSePuedeAnularSiYaSeVendioLoQueEntro() {
        // Restar el stock igual lo dejaria en negativo, que es peor que no
        // poder anular.
        Producto p = productoConCosto(700, 2);
        CompraDTO compra = comprar(p, 5, 700);
        ventaService.registrarVenta(List.of(new com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO(p.getId(), 6)));

        assertThrows(RuntimeException.class, () -> compraService.anularCompra(compra.getId()));
        assertEquals(1.0, productoRepository.findById(p.getId()).orElseThrow().getStock(),
                "el stock no puede haber cambiado por una anulacion que fallo");
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void anularGuardaQuienLoHizo() {
        Producto p = productoConCosto(700, 10);
        CompraDTO compra = comprar(p, 5, 700);

        compraService.anularCompra(compra.getId());

        // Se lee del listado y no del detalle: el detalle recorre la colección
        // de renglones, que es lazy y necesita la sesión abierta (en la
        // aplicación la abre open-in-view, en un test no).
        var enLista = compraService.listarCompras().stream()
                .filter(c -> c.getId().equals(compra.getId()))
                .findFirst().orElseThrow();
        assertEquals("duenio", enLista.getAnuladaPor());
        assertEquals(com.guerram.DespensaFamily.model.EstadoCompra.ANULADA, enLista.getEstado());
    }
}
