package com.guerram.DespensaFamily.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Redondeo de importes al centavo.
 *
 * <p>Mismo problema que {@link Cantidades} resolvio para el stock, pero del
 * lado de la plata: el double no representa los decimales exactos, asi que un
 * costo de 123,45 con 33% de ganancia daba un precio de venta de
 * {@code 164.1885} — cuatro decimales, que despues salian tal cual en la
 * pantalla y en el ticket.
 *
 * <p>El redondeo estaba aplicado en ConfiguracionService pero no en
 * CompraService, que es el otro lugar donde se calcula un precio de venta.
 * Tener la operacion en un solo lado evita que la proxima vez se arregle la
 * mitad otra vez.
 */
class DineroTest {

    @Test
    void redondeaAlCentavo() {
        assertEquals(164.19, Dinero.redondear(164.1885), 0.0001);
    }

    @Test
    void elCasoQueAparecioEnLaAuditoria() {
        // costo 123.45 + 33% de ganancia
        double costo = 123.45;
        assertEquals(164.19, Dinero.redondear(costo + costo * 33 / 100), 0.0001);
    }

    @Test
    void unImporteYaRedondeadoNoCambia() {
        assertEquals(100.0, Dinero.redondear(100.0), 0.0001);
        assertEquals(99.99, Dinero.redondear(99.99), 0.0001);
    }

    @Test
    void redondeaParaArribaDesdeLaMitad() {
        assertEquals(0.13, Dinero.redondear(0.125), 0.0001);
    }

    @Test
    void arreglaLaColaBinariaDelDouble() {
        // 0.1 + 0.2 da 0.30000000000000004 en binario.
        assertEquals(0.3, Dinero.redondear(0.1 + 0.2), 0.0001);
    }

    @Test
    void esIdempotente() {
        double una = Dinero.redondear(164.1885);
        assertEquals(una, Dinero.redondear(una), 0.0001);
    }

    @Test
    void dejaPasarLoQueNoEsUnNumero() {
        // Igual que Cantidades: que la validacion de cada service decida, en
        // vez de convertirlo en un 0 que parece un importe valido.
        assertEquals(Double.NaN, Dinero.redondear(Double.NaN));
        assertEquals(Double.POSITIVE_INFINITY, Dinero.redondear(Double.POSITIVE_INFINITY));
    }
}
