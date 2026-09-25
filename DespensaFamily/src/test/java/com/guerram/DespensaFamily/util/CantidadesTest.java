package com.guerram.DespensaFamily.util;

import com.guerram.DespensaFamily.model.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * DF-DEBT-001: cantidades migradas de double a BigDecimal. El bug que
 * motiva esta clase seguia siendo posible con double aunque Cantidades ya
 * redondeara: vender 1,538 kg de un producto con 5 kg dejaba el stock en
 * 3.4619999999999997 antes de redondear. BigDecimal con escala fija de 3
 * decimales (el gramo) no tiene esa cola binaria para empezar.
 */
class CantidadesTest {

    @Test
    void restarUnPesoNoDejaBasuraDecimal() {
        BigDecimal resultado = new BigDecimal("5").subtract(new BigDecimal("1.538"));
        assertEquals(new BigDecimal("3.462"), Cantidades.redondear(resultado));
    }

    @Test
    void sumarPesosTampocoDejaBasuraDecimal() {
        BigDecimal suma = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        assertEquals(new BigDecimal("0.300"), Cantidades.redondear(suma));
    }

    @Test
    void conservaElGramo() {
        assertEquals(new BigDecimal("0.001"), Cantidades.redondear(new BigDecimal("0.001")));
        assertEquals(new BigDecimal("2.345"), Cantidades.redondear(new BigDecimal("2.345")));
    }

    @Test
    void cortaLoQueSobrepasaElGramo() {
        assertEquals(new BigDecimal("2.346"), Cantidades.redondear(new BigDecimal("2.3456")));
        assertEquals(new BigDecimal("2.345"), Cantidades.redondear(new BigDecimal("2.3454")));
    }

    @Test
    void unNuloNoRompeLaCuenta() {
        assertNull(Cantidades.redondear(null));
    }

    // El stock se redondea al escribirlo y al leerlo: al escribirlo para que la
    // base no acumule basura, y al leerlo para que las filas que ya quedaron
    // sucias antes de este arreglo se muestren bien y no hagan fallar la
    // comparacion "stock < cantidad" al vender lo ultimo que queda.
    @Test
    void elStockDelProductoQuedaRedondeadoAlGuardarlo() {
        Producto producto = new Producto();
        producto.setStock(new BigDecimal("5").subtract(new BigDecimal("1.538")));

        assertEquals(new BigDecimal("3.462"), producto.getStock());
    }

    @Test
    void venderLoUltimoQueQuedaNoDaStockInsuficiente() {
        Producto producto = new Producto();
        producto.setStock(new BigDecimal("5"));
        producto.setStock(producto.getStock().subtract(new BigDecimal("1.538")));
        producto.setStock(producto.getStock().subtract(new BigDecimal("3.462")));

        assertEquals(new BigDecimal("0.000"), producto.getStock());
    }
}
