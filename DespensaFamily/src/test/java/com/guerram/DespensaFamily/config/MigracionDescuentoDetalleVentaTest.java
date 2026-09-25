package com.guerram.DespensaFamily.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DF-UX-005: detalle_venta no guardaba que descuento o promo se habia
 * aplicado a la linea (ticket 196 mostraba "1 u x $2860 = $286" sin
 * explicacion). V15 agrega dos columnas NULLABLE -- descuento_porcentaje y
 * promo_descripcion -- para no romper las ventas ya facturadas.
 *
 * Mismo patron que MigracionBaseDeMartinTest: se siembra el esquema pre-V15
 * con una venta real ya facturada y se corre la cadena completa de
 * migraciones real.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false",
        "spring.datasource.url=jdbc:h2:mem:venta-sin-descuento;DB_CLOSE_DELAY=-1"
})
class MigracionDescuentoDetalleVentaTest {

    static {
        EsquemaDePrueba.sembrar(
                "jdbc:h2:mem:venta-sin-descuento;DB_CLOSE_DELAY=-1", "/db/venta-sin-descuento.sql");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void laVentaVieja_sinDescuentoAplicado_quedaEnNullYNoEnCero() {
        // null y no 0.0: una linea vieja no tiene forma de saber si en su
        // momento hubo o no descuento, asi que dejarla en null (en vez de
        // inventar un 0) es lo unico honesto para el ticket/reporte.
        Double porcentaje = jdbc.queryForObject(
                "select descuento_porcentaje from detalle_venta where id = 1", Double.class);
        String promo = jdbc.queryForObject(
                "select promo_descripcion from detalle_venta where id = 1", String.class);

        assertThat(porcentaje).isNull();
        assertThat(promo).isNull();
    }

    @Test
    void unaLineaNuevaPuedeGuardarSuDescuentoAplicado() {
        jdbc.update(
                "insert into detalle_venta (id, cantidad, precio_costo, precio_unitario, subtotal, "
                        + "descuento_porcentaje, promo_descripcion, producto_id, venta_id) "
                        + "values (2, 1.0, 700.0, 910.0, 819.0, 10.0, null, 1, 1)");

        Double porcentaje = jdbc.queryForObject(
                "select descuento_porcentaje from detalle_venta where id = 2", Double.class);

        assertThat(porcentaje).isEqualTo(10.0);
    }

    @Test
    void conservaLosDatosDelNegocio() {
        assertThat(jdbc.queryForObject("select subtotal from detalle_venta where id = 1", Double.class))
                .isEqualTo(2860.0);
        assertThat(jdbc.queryForObject("select total from venta where id = 1", Double.class))
                .isEqualTo(2860.0);
    }
}
