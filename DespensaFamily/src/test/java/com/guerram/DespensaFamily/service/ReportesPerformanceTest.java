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
        return productoConStock(nombre, precioVenta, 100);
    }

    private Producto productoConStock(String nombre, double precioVenta, double stock) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre)
                .precioCosto(precioVenta / 2).precioVenta(precioVenta).porcentajeGanancia(100)
                .stock(stock).tipoCantidad("UNIDAD").unidadMedida("u")
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
        // La base la comparte toda la corrida (mismo motivo documentado en
        // CajaSaldoTest): otros tests de la suite tambien venden cosas HOY,
        // asi que no se puede asumir que el top solo contiene lo sembrado
        // aca. Se vende una cantidad extrema y unica para garantizar que
        // quede primera sin importar que mas se haya vendido en la corrida.
        Producto ganador = productoConStock("DF-PERF-002 top acotado, ganador garantizado", 10, 1_000_000);
        ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(ganador.getId(), 999_999.0)), MetodoPago.EFECTIVO);

        LocalDate hoy = LocalDate.now();

        // El top=1 tiene que devolver EXACTAMENTE un producto (el limite
        // funciona, no es adorno) y ese uno tiene que ser el ganador.
        List<ProductoMasVendidoDTO> top1 = reportesService.productosMasVendidosTop(hoy, hoy, 1);
        assertThat(top1).hasSize(1);
        assertThat(top1.get(0).getProductoId()).isEqualTo(ganador.getId());
    }

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void unTopMayorQueLaCantidadDeProductosVendidosNoRompe() {
        // Triangulacion: pedir mas de lo que hay no puede fallar ni inventar
        // filas -- simplemente devuelve lo que haya.
        Producto p = producto("DF-PERF-002 top mayor a lo vendido", 500);
        ventaService.registrarVenta(List.of(new DetalleVentaRequestDTO(p.getId(), 2.0)), MetodoPago.EFECTIVO);

        LocalDate hoy = LocalDate.now();
        List<ProductoMasVendidoDTO> top = reportesService.productosMasVendidosTop(hoy, hoy, 10_000);

        assertThat(top).anySatisfy(dto -> assertThat(dto.getProductoId()).isEqualTo(p.getId()));
    }

    private boolean contieneProducto(List<MovimientoDTO> movimientos, Long productoId) {
        return movimientos.stream().anyMatch(m -> productoId.equals(m.getProductoId()));
    }
}
