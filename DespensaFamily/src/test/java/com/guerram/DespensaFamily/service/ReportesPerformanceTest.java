package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoMasVendidoDTO;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * DF-PERF-002 de la auditoria del 23/09: /reportes/movimientos hacia un
 * findById por fila para filtrar productos inactivos (N+1: 2 meses de datos
 * tardaban 1,09 s), y /reportes/productos-mas-vendidos devolvia TODOS los
 * productos vendidos alguna vez (166 KB) en vez de un top acotado. Estos
 * tests prueban el comportamiento correcto -- que el producto inactivo siga
 * sin aparecer y que el top funcione -- sin importar si el filtro esta en el
 * loop de Java o en la consulta SQL; la medicion de performance en si se hizo
 * a mano contra el jar corriendo (ver el reporte).
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class ReportesPerformanceTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ReportesService reportesService;

    private Producto producto(String nombre, double precioVenta) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre)
                .precioCosto(precioVenta / 2).precioVenta(precioVenta).porcentajeGanancia(100)
                .stock(100).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());
    }

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void unProductoDadoDeBajaNoApareceEnProductosMasVendidos() {
        Producto p = producto("DF-PERF-002 mas vendido dado de baja", 1000);
        ventaService.registrarVenta(List.of(new DetalleVentaRequestDTO(p.getId(), 3.0)), MetodoPago.EFECTIVO);

        LocalDate hoy = LocalDate.now();
        assertThat(reportesService.productosMasVendidos(hoy, hoy))
                .anySatisfy(dto -> assertThat(dto.getProductoId()).isEqualTo(p.getId()));

        productoService.eliminarProducto(p.getId());

        assertThat(reportesService.productosMasVendidos(hoy, hoy))
                .noneSatisfy(dto -> assertThat(dto.getProductoId()).isEqualTo(p.getId()));
    }

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void unProductoDadoDeBajaNoApareceEnMovimientos() {
        // Triangulacion con el otro reporte que hacia el mismo N+1
        // (ReportesService.movimientosPorFecha), sobre la misma causa raiz.
        Producto p = producto("DF-PERF-002 movimiento dado de baja", 2000);
        ventaService.registrarVenta(List.of(new DetalleVentaRequestDTO(p.getId(), 1.0)), MetodoPago.EFECTIVO);

        LocalDate hoy = LocalDate.now();
        assertThat(contieneProducto(reportesService.movimientosPorFecha(hoy, hoy, "VENTA"), p.getId())).isTrue();

        productoService.eliminarProducto(p.getId());

        assertThat(contieneProducto(reportesService.movimientosPorFecha(hoy, hoy, "VENTA"), p.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void productosMasVendidosRespetaUnTopAcotado() {
        Producto p1 = producto("DF-PERF-002 top A", 1000);
        Producto p2 = producto("DF-PERF-002 top B", 1000);
        Producto p3 = producto("DF-PERF-002 top C", 1000);

        ventaService.registrarVenta(List.of(new DetalleVentaRequestDTO(p1.getId(), 1.0)), MetodoPago.EFECTIVO);
        ventaService.registrarVenta(List.of(new DetalleVentaRequestDTO(p2.getId(), 5.0)), MetodoPago.EFECTIVO);
        ventaService.registrarVenta(List.of(new DetalleVentaRequestDTO(p3.getId(), 3.0)), MetodoPago.EFECTIVO);

        LocalDate hoy = LocalDate.now();
        List<ProductoMasVendidoDTO> top2 = reportesService.productosMasVendidosTop(hoy, hoy, 2);

        assertThat(top2).hasSize(2);
        // El primero tiene que ser el mas vendido de los tres sembrados en este test.
        assertThat(top2.get(0).getProductoId()).isEqualTo(p2.getId());
    }

    private boolean contieneProducto(List<MovimientoDTO> movimientos, Long productoId) {
        return movimientos.stream().anyMatch(m -> productoId.equals(m.getProductoId()));
    }
}
