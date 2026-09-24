package com.guerram.DespensaFamily.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guerram.DespensaFamily.dto.CompraRequestDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraRequestDTO;
import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Una compra anulada no deberia figurar en el listado de movimientos.
 *
 * <p>Encontrado auditando los reportes: {@code movimientosVentas} filtraba
 * {@code estado = 'ACTIVA'} y {@code movimientosCompras} no filtraba nada. Una
 * compra anulada ya devolvio el stock que sumo y restauro el precio de costo
 * anterior (ver {@link AnularCompraTest}) — pero seguia apareciendo en
 * {@code /reportes/movimientos} como si hubiera pasado de verdad.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class CompraAnuladaEnReportesTest {

    @Autowired
    private CompraService compraService;

    @Autowired
    private ReportesService reportesService;

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void unaCompraAnuladaNoApareceEnMovimientos() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto para anular su compra")
                .precioCosto(100).precioVenta(150).porcentajeGanancia(50)
                .stock(0).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        CompraRequestDTO request = new CompraRequestDTO();
        request.setProveedor("Proveedor de prueba");
        request.setDetalles(List.of(new DetalleCompraRequestDTO(producto.getId(), 10.0, 100.0)));
        request.setMetodoPago(com.guerram.DespensaFamily.model.MetodoPago.EFECTIVO);

        var compra = compraService.registrarCompra(request);

        LocalDate hoy = LocalDate.now();

        assertTrue(
                contieneLaCompra(reportesService.movimientosPorFecha(hoy, hoy, "COMPRA"), compra.getId()),
                "antes de anular, la compra tiene que aparecer");

        compraService.anularCompra(compra.getId());

        assertFalse(
                contieneLaCompra(reportesService.movimientosPorFecha(hoy, hoy, "COMPRA"), compra.getId()),
                "una compra anulada no deberia aparecer en los movimientos");
    }

    private boolean contieneLaCompra(List<MovimientoDTO> movimientos, Long compraId) {
        return movimientos.stream().anyMatch(m -> compraId.equals(m.getOperacionId()));
    }
}
