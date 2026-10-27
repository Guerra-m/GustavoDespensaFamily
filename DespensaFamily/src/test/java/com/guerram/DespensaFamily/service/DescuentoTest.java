package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guerram.DespensaFamily.dto.DescuentoDTO;
import com.guerram.DespensaFamily.model.AlcanceDescuento;
import com.guerram.DespensaFamily.model.Categoria;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.CategoriaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Promos por %: un producto o una categoria entera pueden tener un
 * descuento activo. Si tienen los dos, gana el del producto (mas
 * especifico) — ver DescuentoService.porcentajeEfectivoPara.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class DescuentoTest {

    @Autowired
    private DescuentoService descuentoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    void unProductoSinDescuentoNoTienePorcentaje() {
        Producto producto = crearProducto("Sin descuento", null);
        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualByComparingTo(BigDecimal.valueOf(0.0));
    }

    @Test
    void unDescuentoPorProductoAplicaSoloAEse() {
        Producto conDescuento = crearProducto("Con descuento directo", null);
        Producto sinDescuento = crearProducto("Sin descuento directo", null);

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(conDescuento.getId())
                .porcentaje(BigDecimal.valueOf(20))
                .activo(true)
                .build());

        assertThat(descuentoService.porcentajeEfectivoPara(conDescuento)).isEqualByComparingTo(BigDecimal.valueOf(20.0));
        assertThat(descuentoService.porcentajeEfectivoPara(sinDescuento)).isEqualByComparingTo(BigDecimal.valueOf(0.0));
    }

    @Test
    void unDescuentoPorCategoriaAplicaATodosSusProductos() {
        Categoria categoria = categoriaRepository.save(Categoria.builder()
                .nombre("Categoria con descuento " + System.nanoTime())
                .build());
        Producto producto = crearProducto("Producto de la categoria", categoria);

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.CATEGORIA)
                .categoriaId(categoria.getId())
                .porcentaje(BigDecimal.valueOf(15))
                .activo(true)
                .build());

        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualByComparingTo(BigDecimal.valueOf(15.0));
    }

    @Test
    void elDescuentoDeProductoGanaSobreElDeCategoria() {
        Categoria categoria = categoriaRepository.save(Categoria.builder()
                .nombre("Categoria con descuento propio " + System.nanoTime())
                .build());
        Producto producto = crearProducto("Producto con los dos descuentos", categoria);

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.CATEGORIA)
                .categoriaId(categoria.getId())
                .porcentaje(BigDecimal.valueOf(10))
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(30))
                .activo(true)
                .build());

        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualByComparingTo(BigDecimal.valueOf(30.0));
    }

    @Test
    void desactivarUnDescuentoLoDejaDeAplicar() {
        Producto producto = crearProducto("Producto a desactivar", null);

        DescuentoDTO creado = descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(25))
                .activo(true)
                .build());

        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualByComparingTo(BigDecimal.valueOf(25.0));

        creado.setActivo(false);
        descuentoService.actualizar(creado.getId(), creado);

        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualByComparingTo(BigDecimal.valueOf(0.0));
    }

    // DF-BUG-004: un segundo descuento activo sobre el mismo producto rompe
    // porcentajeEfectivoPara (findByProductoIdAndActivoTrue deja de ser
    // unico) y la venta explota con "Query did not return a unique result".
    // Mismo criterio que PromocionService.validar: no dejar crear/activar
    // un segundo activo para el mismo alcance.
    @Test
    void unSegundoDescuentoActivoSobreElMismoProductoSeRechaza() {
        Producto producto = crearProducto("Leche", null);

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(10))
                .activo(true)
                .build());

        assertThatThrownBy(() -> descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(20))
                .activo(true)
                .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(producto.getNombre())
                .hasMessageContaining("10");
    }

    @Test
    void unSegundoDescuentoActivoSobreLaMismaCategoriaSeRechaza() {
        Categoria categoria = categoriaRepository.save(Categoria.builder()
                .nombre("Lacteos " + System.nanoTime())
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.CATEGORIA)
                .categoriaId(categoria.getId())
                .porcentaje(BigDecimal.valueOf(15))
                .activo(true)
                .build());

        assertThatThrownBy(() -> descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.CATEGORIA)
                .categoriaId(categoria.getId())
                .porcentaje(BigDecimal.valueOf(25))
                .activo(true)
                .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(categoria.getNombre());
    }

    @Test
    void unSegundoDescuentoInactivoSobreElMismoProductoSePermite() {
        Producto producto = crearProducto("Yogur", null);

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(10))
                .activo(true)
                .build());

        DescuentoDTO segundo = descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(20))
                .activo(false)
                .build());

        assertThat(segundo.getId()).isNotNull();
        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualByComparingTo(BigDecimal.valueOf(10.0));
    }

    @Test
    void reactivarUnDescuentoInactivoConOtroActivoSeRechaza() {
        Producto producto = crearProducto("Manteca", null);

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(10))
                .activo(true)
                .build());

        DescuentoDTO inactivo = descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(20))
                .activo(false)
                .build());

        inactivo.setActivo(true);
        assertThatThrownBy(() -> descuentoService.actualizar(inactivo.getId(), inactivo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(producto.getNombre());
    }

    @Test
    void editarElDescuentoActivoExistenteNoChocaConsigoMismo() {
        Producto producto = crearProducto("Harina", null);

        DescuentoDTO creado = descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(10))
                .activo(true)
                .build());

        creado.setPorcentaje(BigDecimal.valueOf(12));
        descuentoService.actualizar(creado.getId(), creado);

        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualByComparingTo(BigDecimal.valueOf(12.0));
    }

    @Test
    void unDescuentoDeProductoYUnoDeCategoriaSobreElMismoProductoSePermiten() {
        Categoria categoria = categoriaRepository.save(Categoria.builder()
                .nombre("Panificados " + System.nanoTime())
                .build());
        Producto producto = crearProducto("Pan lactal", categoria);

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.CATEGORIA)
                .categoriaId(categoria.getId())
                .porcentaje(BigDecimal.valueOf(10))
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(BigDecimal.valueOf(20))
                .activo(true)
                .build());

        // Producto gana sobre categoria, y el metodo sigue devolviendo un
        // unico resultado (antes de este fix, un segundo activo del mismo
        // alcance rompia esto con "no unique result"; el mix producto +
        // categoria nunca estuvo roto porque son columnas distintas, pero
        // se deja el caso cubierto explicitamente).
        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualByComparingTo(BigDecimal.valueOf(20.0));
    }

    private Producto crearProducto(String nombre, Categoria categoria) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre + " " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(100)).precioVenta(BigDecimal.valueOf(200)).porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("UNIDAD").unidadMedida("u")
                .categoria(categoria)
                .activo(true)
                .build());
    }
}
