package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualTo(0.0);
    }

    @Test
    void unDescuentoPorProductoAplicaSoloAEse() {
        Producto conDescuento = crearProducto("Con descuento directo", null);
        Producto sinDescuento = crearProducto("Sin descuento directo", null);

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(conDescuento.getId())
                .porcentaje(20)
                .activo(true)
                .build());

        assertThat(descuentoService.porcentajeEfectivoPara(conDescuento)).isEqualTo(20.0);
        assertThat(descuentoService.porcentajeEfectivoPara(sinDescuento)).isEqualTo(0.0);
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
                .porcentaje(15)
                .activo(true)
                .build());

        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualTo(15.0);
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
                .porcentaje(10)
                .activo(true)
                .build());

        descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(30)
                .activo(true)
                .build());

        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualTo(30.0);
    }

    @Test
    void desactivarUnDescuentoLoDejaDeAplicar() {
        Producto producto = crearProducto("Producto a desactivar", null);

        DescuentoDTO creado = descuentoService.crear(DescuentoDTO.builder()
                .alcance(AlcanceDescuento.PRODUCTO)
                .productoId(producto.getId())
                .porcentaje(25)
                .activo(true)
                .build());

        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualTo(25.0);

        creado.setActivo(false);
        descuentoService.actualizar(creado.getId(), creado);

        assertThat(descuentoService.porcentajeEfectivoPara(producto)).isEqualTo(0.0);
    }

    private Producto crearProducto(String nombre, Categoria categoria) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre + " " + System.nanoTime())
                .precioCosto(100).precioVenta(200).porcentajeGanancia(100)
                .stock(50).tipoCantidad("UNIDAD").unidadMedida("u")
                .categoria(categoria)
                .activo(true)
                .build());
    }
}
