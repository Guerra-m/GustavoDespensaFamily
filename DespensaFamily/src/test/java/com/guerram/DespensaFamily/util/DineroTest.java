package com.guerram.DespensaFamily.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Redondeo de importes al centavo.
 *
 * <p>Migrado de double a BigDecimal (DF-DEBT-001): el double no representa
 * los decimales exactos, asi que un costo de 123,45 con 33% de ganancia daba
 * un precio de venta de {@code 164.1885} — cuatro decimales, que despues
 * salian tal cual en la pantalla y en el ticket. BigDecimal con escala fija
 * y HALF_UP saca el problema de raiz en vez de solo esconder el sintoma con
 * un redondeo manual sobre double.
 *
 * <p>Las comparaciones son con {@code compareTo}/{@code equals} de
 * BigDecimal via assertEquals con dos BigDecimal (que usa equals, sensible a
 * la escala) — por eso siempre se compara contra un valor ya escalado a 2
 * decimales, igual que devuelve {@link Dinero#redondear}.
 */
class DineroTest {

    @Test
    void redondeaAlCentavo() {
        assertEquals(new BigDecimal("164.19"), Dinero.redondear(new BigDecimal("164.1885")));
    }

    @Test
    void elCasoQueAparecioEnLaAuditoria() {
        // costo 123.45 + 33% de ganancia
        BigDecimal costo = new BigDecimal("123.45");
        assertEquals(new BigDecimal("164.19"), Dinero.precioConGanancia(costo, new BigDecimal("33")));
    }

    @Test
    void unImporteYaRedondeadoNoCambia() {
        assertEquals(new BigDecimal("100.00"), Dinero.redondear(new BigDecimal("100.00")));
        assertEquals(new BigDecimal("99.99"), Dinero.redondear(new BigDecimal("99.99")));
    }

    @Test
    void redondeaParaArribaDesdeLaMitad() {
        assertEquals(new BigDecimal("0.13"), Dinero.redondear(new BigDecimal("0.125")));
    }

    @Test
    void arreglaLaColaBinariaDelDouble() {
        // Con double, 0.1 + 0.2 da 0.30000000000000004. Con BigDecimal
        // construido desde String no hay cola binaria que arreglar: la
        // prueba deja constancia de que la suma exacta ya viene bien.
        BigDecimal total = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        assertEquals(new BigDecimal("0.30"), Dinero.redondear(total));
    }

    @Test
    void esIdempotente() {
        BigDecimal una = Dinero.redondear(new BigDecimal("164.1885"));
        assertEquals(una, Dinero.redondear(una));
    }

    @Test
    void dejaPasarNull() {
        // Igual que Cantidades: que la validacion de cada service decida en
        // vez de que Dinero invente un valor.
        assertNull(Dinero.redondear(null));
    }

    @Test
    void precioConGananciaCero() {
        BigDecimal costo = new BigDecimal("50.00");
        assertEquals(new BigDecimal("50.00"), Dinero.precioConGanancia(costo, BigDecimal.ZERO));
    }
}
