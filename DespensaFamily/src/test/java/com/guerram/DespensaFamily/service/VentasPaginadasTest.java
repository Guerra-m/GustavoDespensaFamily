package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.PaginaDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * DF-PERF-001: /ventas sin paginar mandaba 562 KB con 3.055 ventas (~13
 * MB/año). /ventas/pagina es un endpoint NUEVO y no reemplaza a GET /ventas
 * (que sigue devolviendo la lista completa sin params, tal cual la usan
 * home.ts y cualquier otro consumidor que hoy espera un array plano) --
 * asi no hay que auditar cada consumidor existente para blindarlo contra un
 * cambio de forma en la respuesta.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class VentasPaginadasTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ProductoRepository productoRepository;

    private Long crearProductoYVender(String sufijo) {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto paginado " + sufijo + " " + System.nanoTime())
                .precioCosto(100).precioVenta(200).porcentajeGanancia(100)
                .stock(1000).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        VentaDTO venta = ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), 1.0)));
        return venta.getId();
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void devuelveSoloElTamanioDePaginaPedidoYElTotalReal() {
        for (int i = 0; i < 5; i++) crearProductoYVender("a" + i);

        PaginaDTO<VentaDTO> pagina = ventaService.listarVentasPaginado(null, null, 0, 2);

        assertThat(pagina.getContenido()).hasSize(2);
        assertThat(pagina.getTotalElementos()).isGreaterThanOrEqualTo(5);
        assertThat(pagina.getPagina()).isEqualTo(0);
        assertThat(pagina.getTamanio()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void laSegundaPaginaTraeDistintasVentasQueLaPrimera() {
        for (int i = 0; i < 5; i++) crearProductoYVender("b" + i);

        PaginaDTO<VentaDTO> primera = ventaService.listarVentasPaginado(null, null, 0, 2);
        PaginaDTO<VentaDTO> segunda = ventaService.listarVentasPaginado(null, null, 1, 2);

        List<Long> idsPrimera = primera.getContenido().stream().map(VentaDTO::getId).toList();
        List<Long> idsSegunda = segunda.getContenido().stream().map(VentaDTO::getId).toList();

        assertThat(idsSegunda).doesNotContainAnyElementsOf(idsPrimera);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void filtraPorRangoDeFechasCuandoSePide() {
        Long idVieja = crearProductoYVender("vieja");
        // Sin setter directo de fecha desde el service (registrarVenta siempre
        // usa "ahora"): se usa un rango que deliberadamente EXCLUYE la fecha
        // actual para comprobar que el filtro de verdad filtra.
        LocalDateTime desde = LocalDateTime.now().plusYears(1);
        LocalDateTime hasta = LocalDateTime.now().plusYears(2);

        PaginaDTO<VentaDTO> pagina = ventaService.listarVentasPaginado(desde, hasta, 0, 50);

        assertThat(pagina.getContenido()).noneMatch(v -> v.getId().equals(idVieja));
        assertThat(pagina.getTotalElementos()).isZero();
    }
}
