package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.AnularCompraResponseDTO;
import com.guerram.DespensaFamily.dto.CompraDTO;
import com.guerram.DespensaFamily.dto.CompraRequestDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraRequestDTO;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DF-BUG-005: anular una compra no puede pisar un cambio de precio manual
 * hecho DESPUES de esa compra, ni el resultado de una compra posterior sobre
 * el mismo producto.
 *
 * <p>Evidencia de la auditoria: Torta raspada, compra -> precio manual
 * $1.500 -> anular compra -> el precio volvia a $650 (el de antes de la
 * compra), pisando la edicion manual sin avisar.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class AnularCompraConservaPrecioManualTest {

    @Autowired
    private CompraService compraService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoRepository productoRepository;

    private Producto productoConCosto(double costo, double stock) {
        return productoRepository.save(Producto.builder()
                .nombre("Torta de prueba")
                .precioCosto(costo).precioVenta(costo * 2).porcentajeGanancia(100)
                .stock(stock).tipoCantidad("UNIDAD").unidadMedida("u")
                .fraccionMinima(1.0).activo(true)
                .build());
    }

    private CompraDTO comprar(Producto p, double cantidad, double costo) {
        CompraRequestDTO pedido = new CompraRequestDTO();
        pedido.setProveedor("Mayorista");
        pedido.setDetalles(List.of(new DetalleCompraRequestDTO(p.getId(), cantidad, costo)));
        pedido.setMetodoPago(MetodoPago.EFECTIVO);
        return compraService.registrarCompra(pedido);
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void anularNoPisaUnPrecioDeVentaEditadoAMano() {
        // costo 500 -> compra a 800: precio de venta pasa a $650 -> $1.600
        // (100% de ganancia), como en la evidencia de la auditoria.
        Producto p = productoConCosto(500, 5);
        CompraDTO compra = comprar(p, 10, 800);
        assertThat(productoRepository.findById(p.getId()).orElseThrow().getPrecioVenta()).isEqualTo(1600.0);

        // Edicion manual del precio de venta DESPUES de la compra (PATCH,
        // no toca el costo).
        productoService.actualizarPrecioYStock(p.getId(), 1500.0, null, null);

        AnularCompraResponseDTO respuesta = compraService.anularCompra(compra.getId());

        Producto despues = productoRepository.findById(p.getId()).orElseThrow();
        assertThat(despues.getPrecioVenta())
                .as("el precio manual tiene que sobrevivir a la anulacion")
                .isEqualTo(1500.0);
        assertThat(despues.getPrecioCosto())
                .as("el costo tampoco se toca cuando el precio de venta se conservo")
                .isEqualTo(800.0);
        assertThat(despues.getStock())
                .as("el stock si se deshace, eso no cambia")
                .isEqualTo(5.0);
        assertThat(respuesta.getProductosConPrecioConservado())
                .as("la respuesta tiene que avisar que este producto conservo su precio")
                .containsExactly("Torta de prueba");
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void anularSinCambiosIntermediosSigueRestaurandoComoAntes() {
        Producto p = productoConCosto(500, 5);
        CompraDTO compra = comprar(p, 10, 800);

        AnularCompraResponseDTO respuesta = compraService.anularCompra(compra.getId());

        Producto despues = productoRepository.findById(p.getId()).orElseThrow();
        assertThat(despues.getPrecioCosto()).isEqualTo(500.0);
        assertThat(despues.getPrecioVenta()).isEqualTo(1000.0);
        assertThat(respuesta.getProductosConPrecioConservado())
                .as("sin cambios intermedios, no hay nada que avisar")
                .isEmpty();
    }

    @Test
    @WithMockUser(username = "duenio", roles = "ADMIN")
    void dosComprasSeguidasDelMismoProductoYAnularLaPrimeraConservaElPrecioDeLaSegunda() {
        Producto p = productoConCosto(500, 0);
        CompraDTO primera = comprar(p, 5, 700); // costo 500 -> 700
        CompraDTO segunda = comprar(p, 5, 900); // costo 700 -> 900

        AnularCompraResponseDTO respuesta = compraService.anularCompra(primera.getId());

        Producto despues = productoRepository.findById(p.getId()).orElseThrow();
        assertThat(despues.getPrecioCosto())
                .as("el costo lo fijo la segunda compra, anular la primera no lo puede pisar")
                .isEqualTo(900.0);
        assertThat(despues.getPrecioVenta()).isEqualTo(1800.0);
        assertThat(respuesta.getProductosConPrecioConservado()).containsExactly("Torta de prueba");

        // La segunda compra, en cambio, si puede anularse y restaurar en
        // cascada hasta el costo que dejo la primera.
        AnularCompraResponseDTO respuestaSegunda = compraService.anularCompra(segunda.getId());
        Producto finalProducto = productoRepository.findById(p.getId()).orElseThrow();
        assertThat(finalProducto.getPrecioCosto()).isEqualTo(700.0);
        assertThat(respuestaSegunda.getProductosConPrecioConservado()).isEmpty();
    }
}
