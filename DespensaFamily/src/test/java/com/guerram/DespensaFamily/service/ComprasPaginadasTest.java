package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.CompraDTO;
import com.guerram.DespensaFamily.dto.CompraRequestDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraRequestDTO;
import com.guerram.DespensaFamily.dto.PaginaDTO;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * DF-PERF-001: mismo problema que /ventas pero en /compras. Endpoint NUEVO
 * (listarComprasPaginado), GET /compras sigue devolviendo todo sin filtros.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class ComprasPaginadasTest {

    @Autowired
    private CompraService compraService;

    @Autowired
    private ProductoRepository productoRepository;

    private void crearCompra(String sufijo) {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto compra paginada " + sufijo + " " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(50)).precioVenta(BigDecimal.valueOf(75)).porcentajeGanancia(BigDecimal.valueOf(50))
                .stock(BigDecimal.valueOf(0)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        CompraRequestDTO request = new CompraRequestDTO();
        request.setProveedor("Proveedor paginado");
        request.setDetalles(List.of(new DetalleCompraRequestDTO(producto.getId(), BigDecimal.valueOf(1.0), BigDecimal.valueOf(50.0))));
        request.setMetodoPago(MetodoPago.EFECTIVO);

        compraService.registrarCompra(request);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void devuelveSoloElTamanioDePaginaPedidoYElTotalReal() {
        for (int i = 0; i < 5; i++) crearCompra("a" + i);

        PaginaDTO<CompraDTO> pagina = compraService.listarComprasPaginado(null, null, 0, 2);

        assertThat(pagina.getContenido()).hasSize(2);
        assertThat(pagina.getTotalElementos()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void laSegundaPaginaTraeDistintasComprasQueLaPrimera() {
        for (int i = 0; i < 5; i++) crearCompra("b" + i);

        PaginaDTO<CompraDTO> primera = compraService.listarComprasPaginado(null, null, 0, 2);
        PaginaDTO<CompraDTO> segunda = compraService.listarComprasPaginado(null, null, 1, 2);

        List<Long> idsPrimera = primera.getContenido().stream().map(CompraDTO::getId).toList();
        List<Long> idsSegunda = segunda.getContenido().stream().map(CompraDTO::getId).toList();

        assertThat(idsSegunda).doesNotContainAnyElementsOf(idsPrimera);
    }
}
