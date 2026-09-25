package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.DescuentoDTO;
import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.dto.VentaDetalleResponseDTO;
import com.guerram.DespensaFamily.model.AlcanceDescuento;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.Promocion;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.PromocionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * El precio que se cobra en una venta tiene que reflejar el % de descuento
 * activo del producto — calculado en el servidor (VentaService), no
 * confiado del cliente, mismo criterio que ya regía para Promocion.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class VentaConDescuentoTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private DescuentoService descuentoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PromocionRepository promocionRepository;

    // DF-UX-005: la venta tiene que guardar QUE descuento se aplico, no solo
    // cobrarlo bien. Ticket 196 mostraba "1 u x $2860 = $286" porque
    // DetalleVenta no tenia donde guardar el 90% que se le aplico.
    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void unaVentaConDescuentoGuardaElPorcentajeEnElDetalle() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Galletitas con 90% off " + System.nanoTime())
                .precioCosto(500).precioVenta(2860).porcentajeGanancia(100)
                .stock(50).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(90)
                .activo(true)
                .build());

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), 1.0)));

        VentaDetalleResponseDTO detalle = ventaService.buscarDetallePorId(venta.getId());

        assertThat(detalle.getDetalles()).hasSize(1);
        assertThat(detalle.getDetalles().get(0).getDescuentoPorcentaje()).isEqualTo(90.0);
        assertThat(detalle.getDetalles().get(0).getPromoDescripcion()).isNull();
        // El precio de lista sigue siendo el de venta, sin el descuento
        // aplicado -- lo aplicado va en subtotal. El ticket necesita las dos
        // cosas para mostrar "precio lista, descuento, subtotal".
        assertThat(detalle.getDetalles().get(0).getPrecioUnitario()).isEqualTo(2860.0);
        assertThat(detalle.getDetalles().get(0).getSubtotal()).isEqualTo(286.0);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void unaVentaConPromoGuardaLaDescripcionDeLaPromoYSinPorcentaje() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Yerba 3x2 " + System.nanoTime())
                .precioCosto(500).precioVenta(1000).porcentajeGanancia(100)
                .stock(50).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        promocionRepository.save(Promocion.builder()
                .producto(producto)
                .cantidadLleva(3)
                .cantidadPaga(2)
                .activo(true)
                .build());

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), 3.0)));

        VentaDetalleResponseDTO detalle = ventaService.buscarDetallePorId(venta.getId());

        assertThat(detalle.getDetalles().get(0).getPromoDescripcion()).isEqualTo("3x2");
        assertThat(detalle.getDetalles().get(0).getDescuentoPorcentaje()).isNull();
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void unaVentaSinDescuentoNiPromoQuedaEnNullEnLosDos() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto sin promo ni descuento " + System.nanoTime())
                .precioCosto(500).precioVenta(1000).porcentajeGanancia(100)
                .stock(50).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), 1.0)));

        VentaDetalleResponseDTO detalle = ventaService.buscarDetallePorId(venta.getId());

        assertThat(detalle.getDetalles().get(0).getDescuentoPorcentaje()).isNull();
        assertThat(detalle.getDetalles().get(0).getPromoDescripcion()).isNull();
    }

    // Cotizar (lo que muestra el carrito) y registrar (lo que se cobra)
    // tienen que coincidir en el mismo % -- ambos pasan por calcularLinea,
    // pero si algun dia se separan este test lo agarra.
    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void cotizarYRegistrarCoincidenEnElPorcentajeDeDescuento() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Leche con 10% off " + System.nanoTime())
                .precioCosto(700).precioVenta(910).porcentajeGanancia(30)
                .stock(50).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(10)
                .activo(true)
                .build());

        var cotizacion = ventaService.cotizar(List.of(
                new DetalleVentaRequestDTO(producto.getId(), 1.0)));

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), 1.0)));
        VentaDetalleResponseDTO detalle = ventaService.buscarDetallePorId(venta.getId());

        assertThat(cotizacion.getLineas().get(0).getDescuentoPorcentaje())
                .isEqualTo(detalle.getDetalles().get(0).getDescuentoPorcentaje());
        assertThat(cotizacion.getTotal()).isEqualTo(venta.getTotal());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void unDescuentoDel20PorCientoCobraElPrecioConDescuento() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto con 20% off " + System.nanoTime())
                .precioCosto(500).precioVenta(1000).porcentajeGanancia(100)
                .stock(50).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(20)
                .activo(true)
                .build());

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), 2.0)));

        // 2 unidades a $1000 con 20% off: $1600, no $2000.
        assertThat(venta.getTotal()).isEqualTo(1600.0);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void unProductoPesableTambienAplicaElDescuento() {
        // El combo (Promocion) solo aplica a UNIDAD; el descuento por % tiene
        // que funcionar tambien con PESO, que es justo el caso que Promocion
        // no cubre.
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Fiambre con descuento " + System.nanoTime())
                .precioCosto(1000).precioVenta(2000).porcentajeGanancia(100)
                .stock(50).tipoCantidad("PESO").unidadMedida("kg")
                .fraccionMinima(0.001)
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(10)
                .activo(true)
                .build());

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), 1.0)));

        // 1kg a $2000 con 10% off: $1800.
        assertThat(venta.getTotal()).isEqualTo(1800.0);
    }
}
