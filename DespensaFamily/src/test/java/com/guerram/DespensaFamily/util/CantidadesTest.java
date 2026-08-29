package com.guerram.DespensaFamily.util;

import com.guerram.DespensaFamily.model.Producto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El bug que motiva esta clase: vender 1,538 kg de un producto con 5 kg dejaba
 * el stock en 3.4619999999999997 y la pantalla de productos lo mostraba asi.
 */
class CantidadesTest {

    @Test
    void restarUnPesoNoDejaBasuraDecimal() {
        assertEquals(3.462, Cantidades.redondear(5 - 1.538));
    }

    @Test
    void sumarPesosTampocoDejaBasuraDecimal() {
        assertEquals(0.3, Cantidades.redondear(0.1 + 0.2));
    }

    @Test
    void conservaElGramo() {
        assertEquals(0.001, Cantidades.redondear(0.001));
        assertEquals(2.345, Cantidades.redondear(2.345));
    }

    @Test
    void cortaLoQueSobrepasaElGramo() {
        assertEquals(2.346, Cantidades.redondear(2.3456));
        assertEquals(2.345, Cantidades.redondear(2.3454));
    }

    @Test
    void unNumeroQueNoEsNumeroNoRompeLaCuenta() {
        assertEquals(Double.NaN, Cantidades.redondear(Double.NaN));
        assertEquals(Double.POSITIVE_INFINITY, Cantidades.redondear(Double.POSITIVE_INFINITY));
    }

    // El stock se redondea al escribirlo y al leerlo: al escribirlo para que la
    // base no acumule basura, y al leerlo para que las filas que ya quedaron
    // sucias antes de este arreglo se muestren bien y no hagan fallar la
    // comparacion "stock < cantidad" al vender lo ultimo que queda.
    @Test
    void elStockDelProductoQuedaRedondeadoAlGuardarlo() {
        Producto producto = new Producto();
        producto.setStock(5 - 1.538);

        assertEquals(3.462, producto.getStock());
    }

    @Test
    void venderLoUltimoQueQuedaNoDaStockInsuficiente() {
        Producto producto = new Producto();
        producto.setStock(5);
        producto.setStock(producto.getStock() - 1.538);
        producto.setStock(producto.getStock() - 3.462);

        assertEquals(0.0, producto.getStock());
    }
}
