package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.DescuentoDTO;
import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.model.AlcanceDescuento;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
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
