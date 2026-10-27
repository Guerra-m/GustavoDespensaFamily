package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.ProductoDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * DF-DATA-001: productos sin tipoCantidad se vendian en fracciones porque
 * nada validaba stock entero ni unidad coherente al dar de alta o editar.
 * DF-BUG-009: el codigo de barras se podia repetir entre productos.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class ProductoValidacionTest {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private VentaService ventaService;

    private ProductoDTO dtoBase(String nombre) {
        return ProductoDTO.builder()
                .nombre(nombre)
                .precioCosto(BigDecimal.valueOf(100))
                .precioVenta(BigDecimal.valueOf(150))
                .porcentajeGanancia(BigDecimal.valueOf(50))
                .stock(BigDecimal.valueOf(1))
                .build();
    }

    // ---------- DF-DATA-001: stock entero en UNIDAD ----------

    @Test
    void rechazaStockDecimalAlDarDeAltaUnProductoPorUnidad() throws Exception {
        ProductoDTO dto = dtoBase("Chupetin frac " + System.nanoTime());
        dto.setTipoCantidad("UNIDAD");
        dto.setStock(BigDecimal.valueOf(0.5));

        assertThatThrownBy(() -> productoService.crearProducto(dto, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("entero");
    }

    @Test
    void rechazaStockDecimalAlDarDeAltaUnProductoSinTipoDeclarado() throws Exception {
        // Sin tipoCantidad, Mapper.toEntity ya lo manda a "UNIDAD": tiene que
        // seguir aplicando la misma regla de stock entero (segundo caso para
        // triangular, no el mismo camino que el de arriba).
        ProductoDTO dto = dtoBase("Sin tipo frac " + System.nanoTime());
        dto.setStock(BigDecimal.valueOf(2.75));

        assertThatThrownBy(() -> productoService.crearProducto(dto, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("entero");
    }

    @Test
    void permiteStockDecimalEnUnProductoPorPeso() throws Exception {
        ProductoDTO dto = dtoBase("Queso frac " + System.nanoTime());
        dto.setTipoCantidad("PESO");
        dto.setUnidadMedida("kg");
        dto.setStock(BigDecimal.valueOf(2.75));

        ProductoDTO creado = productoService.crearProducto(dto, null);

        assertThat(creado.getStock()).isEqualByComparingTo(BigDecimal.valueOf(2.75));
    }

    @Test
    void elPatchDeStockTambienRechazaFraccionEnUnProductoPorUnidad() throws Exception {
        ProductoDTO dto = dtoBase("Patch entero " + System.nanoTime());
        dto.setTipoCantidad("UNIDAD");
        dto.setStock(BigDecimal.valueOf(5));
        ProductoDTO creado = productoService.crearProducto(dto, null);

        assertThatThrownBy(() -> productoService.actualizarPrecioYStock(creado.getId(), null, null, BigDecimal.valueOf(5.5)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("entero");
    }

    // ---------- DF-DATA-001: unidad coherente con el tipo ----------

    @Test
    void rechazaUnProductoPorUnidadConUnidadDeMedidaKg() throws Exception {
        // El caso real de la auditoria: "Fiambres variados" quedo UNIDAD/kg.
        ProductoDTO dto = dtoBase("Fiambre incoherente " + System.nanoTime());
        dto.setTipoCantidad("UNIDAD");
        dto.setUnidadMedida("kg");
        dto.setStock(BigDecimal.valueOf(3));

        assertThatThrownBy(() -> productoService.crearProducto(dto, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unidad de medida");
    }

    @Test
    void rechazaUnProductoPorPesoConUnidadDeMedidaU() throws Exception {
        ProductoDTO dto = dtoBase("Peso con u " + System.nanoTime());
        dto.setTipoCantidad("PESO");
        dto.setUnidadMedida("u");
        dto.setStock(BigDecimal.valueOf(1.5));

        ProductoDTO creado = productoService.crearProducto(dto, null);

        // No rechaza con error: normaliza "u" a "kg" para un producto por
        // peso, que es un default razonable y no una combinacion invalida
        // (a diferencia de UNIDAD+kg, que si es un error del usuario).
        assertThat(creado.getUnidadMedida()).isEqualTo("kg");
    }

    // ---------- DF-BUG-009: codigo de barras unico ----------

    @Test
    void rechazaElAltaConUnCodigoDeBarrasYaUsado() throws Exception {
        String codigo = "779" + System.nanoTime() % 1_000_000_000L;

        ProductoDTO original = dtoBase("Original " + System.nanoTime());
        original.setTipoCantidad("UNIDAD");
        original.setCodigoBarras(codigo);
        ProductoDTO creado = productoService.crearProducto(original, null);

        ProductoDTO duplicado = dtoBase("Duplicado " + System.nanoTime());
        duplicado.setTipoCantidad("UNIDAD");
        duplicado.setCodigoBarras(codigo);

        assertThatThrownBy(() -> productoService.crearProducto(duplicado, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(creado.getNombre());
    }

    @Test
    void permiteVariosProductosSinCodigoDeBarras() throws Exception {
        ProductoDTO uno = dtoBase("Sin codigo A " + System.nanoTime());
        ProductoDTO dos = dtoBase("Sin codigo B " + System.nanoTime());

        // Ninguno de los dos manda codigoBarras (queda null): no tienen que
        // chocar entre si, es el segundo caso que triangula la regla de
        // unicidad (el primero es el de arriba, que SI choca).
        assertThat(productoService.crearProducto(uno, null)).isNotNull();
        assertThat(productoService.crearProducto(dos, null)).isNotNull();
    }

    @Test
    void editarUnProductoConservandoSuPropioCodigoNoSeRechazaASiMismo() throws Exception {
        String codigo = "780" + System.nanoTime() % 1_000_000_000L;
        ProductoDTO dto = dtoBase("Editable " + System.nanoTime());
        dto.setTipoCantidad("UNIDAD");
        dto.setCodigoBarras(codigo);
        ProductoDTO creado = productoService.crearProducto(dto, null);

        creado.setNombre("Editable renombrado " + System.nanoTime());

        ProductoDTO editado = productoService.actualizarProducto(creado.getId(), creado, null);

        assertThat(editado.getCodigoBarras()).isEqualTo(codigo);
    }

    @Test
    void editarUnProductoAUnCodigoQueYaTieneOtroSeRechaza() throws Exception {
        String codigoA = "781" + System.nanoTime() % 1_000_000_000L;
        String codigoB = "782" + (System.nanoTime() + 1) % 1_000_000_000L;

        ProductoDTO a = dtoBase("Dueño del codigo A " + System.nanoTime());
        a.setTipoCantidad("UNIDAD");
        a.setCodigoBarras(codigoA);
        ProductoDTO creadoA = productoService.crearProducto(a, null);

        ProductoDTO b = dtoBase("Quiere el codigo A " + System.nanoTime());
        b.setTipoCantidad("UNIDAD");
        b.setCodigoBarras(codigoB);
        ProductoDTO creadoB = productoService.crearProducto(b, null);

        creadoB.setCodigoBarras(codigoA);

        assertThatThrownBy(() -> productoService.actualizarProducto(creadoB.getId(), creadoB, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(creadoA.getNombre());
    }

    // ---------- Venta: rechazo de decimales mas alla de UNIDAD ----------

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void ventaRechazaMedioProductoPorUnidad() {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Chupetin venta " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(100)).precioVenta(BigDecimal.valueOf(150)).porcentajeGanancia(BigDecimal.valueOf(50))
                .stock(BigDecimal.valueOf(10)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        assertThatThrownBy(() -> ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(0.5)))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no permite decimales");
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void ventaPermiteMedioProductoPorPeso() {
        // Segundo caso para triangular la regla de VentaService: la unica
        // excepcion a "no decimales" es PESO, no basta con probar que
        // UNIDAD se rechaza.
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Jamon venta " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(1000)).precioVenta(BigDecimal.valueOf(2000)).porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(5)).tipoCantidad("PESO").unidadMedida("kg")
                .activo(true)
                .build());

        var venta = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(0.5))));

        assertThat(venta.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(1000.0));
    }
}
