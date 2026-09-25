package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guerram.DespensaFamily.model.DetalleVenta;
import com.guerram.DespensaFamily.model.EstadoVenta;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.Venta;
import com.guerram.DespensaFamily.repository.MovimientoCajaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.VentaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * DF-DEBT-003: los demas caminos que suman/restan stock (registrarVenta,
 * CompraService.registrarCompra/anularCompra) SIEMPRE pasan por
 * {@code buscarParaActualizarStock} (el {@code SELECT ... FOR UPDATE} que
 * evita perder un ajuste de stock bajo concurrencia). anularVenta se habia
 * quedado afuera: tocaba {@code detalle.getProducto()} directo, la entidad
 * ya traida SIN lock por el {@code findById} de la venta. Unit test con
 * mocks (no @SpringBootTest) para verificar la interaccion exacta con el
 * repositorio, sin depender de que una carrera real se gane o se pierda.
 */
class AnularVentaUsaLockDeStockTest {

    @Test
    void anularVentaPideElProductoConLockPesimistaAntesDeSumarleElStock() {
        VentaRepository ventaRepository = mock(VentaRepository.class);
        ProductoRepository productoRepository = mock(ProductoRepository.class);
        PromocionService promocionService = mock(PromocionService.class);
        DescuentoService descuentoService = mock(DescuentoService.class);
        CajaService cajaService = mock(CajaService.class);
        MovimientoCajaRepository movimientoCajaRepository = mock(MovimientoCajaRepository.class);

        VentaService ventaService = new VentaService(
                ventaRepository, productoRepository, promocionService, descuentoService,
                cajaService, movimientoCajaRepository);

        Producto producto = Producto.builder()
                .id(7L)
                .nombre("Producto anulado")
                .stock(BigDecimal.valueOf(5))
                .build();

        DetalleVenta detalle = new DetalleVenta();
        detalle.setProducto(producto);
        detalle.setCantidad(BigDecimal.valueOf(3));

        Venta venta = Venta.builder()
                .id(42L)
                .estado(EstadoVenta.ACTIVA)
                .detalles(List.of(detalle))
                .build();

        when(ventaRepository.findById(42L)).thenReturn(Optional.of(venta));
        // El mock devuelve el MISMO producto: lo que importa ac es que el
        // metodo con lock se haya invocado, no reproducir el lock en si
        // (eso ya lo prueba, contra una base real, buscarParaActualizarStock
        // en el repositorio — ver el @Lock de ProductoRepository).
        when(productoRepository.buscarParaActualizarStock(7L)).thenReturn(Optional.of(producto));
        when(movimientoCajaRepository.findByVentaIdAndCategoriaAndRevierteAIsNull(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        ventaService.anularVenta(42L);

        // La aserción central del test: anularVenta tiene que pasar por el
        // mismo camino con lock que registrarVenta/CompraService, no leer el
        // producto ya cargado (sin lock) desde detalle.getProducto().
        verify(productoRepository).buscarParaActualizarStock(7L);
        assertThat(producto.getStock()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.ANULADA);
    }
}
