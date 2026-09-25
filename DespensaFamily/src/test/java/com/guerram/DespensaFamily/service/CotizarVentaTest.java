package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.CotizacionDTO;
import com.guerram.DespensaFamily.dto.DescuentoDTO;
import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.model.AlcanceDescuento;
import com.guerram.DespensaFamily.model.Categoria;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.Promocion;
import com.guerram.DespensaFamily.repository.CategoriaRepository;
import com.guerram.DespensaFamily.repository.MovimientoCajaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.PromocionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * DF-BUG-001: /ventas/cotizar tiene que devolver EXACTAMENTE lo mismo que
 * despues cobra registrarVenta, porque comparten el mismo metodo de calculo
 * (VentaService.calcularLinea) — no hay una segunda cuenta en ningun lado.
 *
 * <p>Reproducido en vivo: el carrito del frontend recalculaba el total el
 * mismo (aplicando solo promos, nunca descuentos, y sin redondear cantidades
 * pesables), y mostraba $2.860 en pantalla cuando el ticket y la base
 * quedaban en $286.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class CotizarVentaTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private DescuentoService descuentoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private PromocionRepository promocionRepository;

    @Autowired
    private MovimientoCajaRepository movimientoCajaRepository;

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void cotizarConDescuentoPorProductoCoincideConLaVentaReal() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Cotizar - descuento producto " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(500)).precioVenta(BigDecimal.valueOf(1000)).porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(90))
                .activo(true)
                .build());

        CotizacionDTO cotizacion = ventaService.cotizar(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(2.0))));

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(2.0))));

        assertThat(cotizacion.getTotal()).isEqualByComparingTo(venta.getTotal());
        assertThat(cotizacion.getLineas()).hasSize(1);
        assertThat(cotizacion.getLineas().get(0).getDescuentoPorcentaje()).isEqualByComparingTo(BigDecimal.valueOf(90.0));
        assertThat(cotizacion.getLineas().get(0).getPromoDescripcion()).isNull();
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void cotizarConDescuentoPorCategoriaCoincideConLaVentaReal() {
        Categoria categoria = categoriaRepository.save(Categoria.builder()
                .nombre("Categoria cotizar " + System.nanoTime())
                .build());

        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Cotizar - descuento categoria " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(200)).precioVenta(BigDecimal.valueOf(500)).porcentajeGanancia(BigDecimal.valueOf(150))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("UNIDAD").unidadMedida("u")
                .categoria(categoria)
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.CATEGORIA)
                .categoriaId(categoria.getId())
                .porcentaje(BigDecimal.valueOf(30))
                .activo(true)
                .build());

        CotizacionDTO cotizacion = ventaService.cotizar(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(3.0))));

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(3.0))));

        assertThat(cotizacion.getTotal()).isEqualByComparingTo(venta.getTotal());
        assertThat(cotizacion.getLineas().get(0).getDescuentoPorcentaje()).isEqualByComparingTo(BigDecimal.valueOf(30.0));
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void cotizarConPromo3x2CoincideConLaVentaReal() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Cotizar - promo 3x2 " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(600)).precioVenta(BigDecimal.valueOf(1000)).porcentajeGanancia(BigDecimal.valueOf(66.67))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        promocionRepository.save(Promocion.builder()
                .producto(producto)
                .cantidadLleva(3)
                .cantidadPaga(2)
                .activo(true)
                .build());

        CotizacionDTO cotizacion = ventaService.cotizar(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(3.0))));

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(3.0))));

        assertThat(cotizacion.getTotal()).isEqualByComparingTo(venta.getTotal());
        assertThat(cotizacion.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(2000.0));
        assertThat(cotizacion.getLineas().get(0).getPromoDescripcion()).isEqualTo("3x2");
        assertThat(cotizacion.getLineas().get(0).getDescuentoPorcentaje()).isNull();
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void siHayDescuentoYPromoJuntosGanaElDescuentoEnLosDosLados() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Cotizar - descuento y promo " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(400)).precioVenta(BigDecimal.valueOf(1000)).porcentajeGanancia(BigDecimal.valueOf(150))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(50))
                .activo(true)
                .build());

        promocionRepository.save(Promocion.builder()
                .producto(producto)
                .cantidadLleva(3)
                .cantidadPaga(2)
                .activo(true)
                .build());

        CotizacionDTO cotizacion = ventaService.cotizar(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(3.0))));

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(3.0))));

        // Gana el descuento (50%): 3 * 1000 * 0.5 = 1500, no el 3x2 (2000).
        assertThat(cotizacion.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(1500.0));
        assertThat(cotizacion.getTotal()).isEqualByComparingTo(venta.getTotal());
        assertThat(cotizacion.getLineas().get(0).getDescuentoPorcentaje()).isEqualByComparingTo(BigDecimal.valueOf(50.0));
        assertThat(cotizacion.getLineas().get(0).getPromoDescripcion()).isNull();
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void cotizarUnProductoPesadoRedondeaLaCantidadIgualQueLaVentaReal() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Cotizar - pesable " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(1000)).precioVenta(BigDecimal.valueOf(2600)).porcentajeGanancia(BigDecimal.valueOf(160))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("PESO").unidadMedida("kg")
                .fraccionMinima(BigDecimal.valueOf(0.001))
                .activo(true)
                .build());

        // 0.3333 kg tiene que quedar en 0.333 (redondeo a la milesima, ver
        // util.Cantidades) tanto en la cotizacion como en la venta real.
        CotizacionDTO cotizacion = ventaService.cotizar(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(0.3333))));

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(0.3333))));

        assertThat(cotizacion.getLineas().get(0).getCantidad()).isEqualByComparingTo(BigDecimal.valueOf(0.333));
        assertThat(cotizacion.getTotal()).isEqualByComparingTo(venta.getTotal());
        assertThat(cotizacion.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(865.80));
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void cotizarNoTocaStockNiCaja() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Cotizar - no debe tocar nada " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(500)).precioVenta(BigDecimal.valueOf(1000)).porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        long movimientosAntes = movimientoCajaRepository.count();

        ventaService.cotizar(List.of(new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(5.0))));

        Producto releido = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(releido.getStock()).isEqualByComparingTo(BigDecimal.valueOf(50.0));
        assertThat(movimientoCajaRepository.count()).isEqualTo(movimientosAntes);
    }
}
