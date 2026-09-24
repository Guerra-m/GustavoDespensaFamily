package com.guerram.DespensaFamily.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La misma migracion, pero partiendo de una base anterior al 01/09: sin la tabla
 * configuracion, sin rastro de quien vendio, sin precio_costo_anterior y con
 * fecha_hora todavia como DATE.
 *
 * Es el caso que evita que V1 sea una curita para la base de una sola maquina.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false",
        "spring.datasource.url=jdbc:h2:mem:base-vieja;DB_CLOSE_DELAY=-1"
})
class MigracionEsquemaViejoTest {

    // Igual que en el otro test: sembrar aca y no con INIT en la URL, porque
    // INIT corre en cada conexion del pool y no una sola vez.
    static {
        EsquemaDePrueba.sembrar(
                "jdbc:h2:mem:base-vieja;DB_CLOSE_DELAY=-1", "/db/esquema-pre-01-09.sql");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void creaLaTablaDeAjustesQueNoExistia() {
        assertThat(jdbc.queryForObject("select count(*) from configuracion", Integer.class))
                .isNotNull();
    }

    @Test
    void laVentaQueYaEstabaQuedaActivaYNoAnulada() {
        // estado es NOT NULL en el modelo. Agregarlo a una tabla con ventas
        // adentro solo funciona si la migracion les da un valor: una venta
        // vieja es una venta que se hizo, no una anulada.
        assertThat(jdbc.queryForObject("select estado from venta where id = 1", String.class))
                .isEqualTo("ACTIVA");
        assertThat(jdbc.queryForObject("select anulada_por from venta where id = 1", String.class))
                .isNull();
    }

    @Test
    void fechaHoraPasaDeDiaAFechaConHora() {
        Timestamp fecha = jdbc.queryForObject(
                "select fecha_hora from venta where id = 1", Timestamp.class);

        // El dia se conserva; lo que cambia es que ahora la columna puede
        // guardar la hora, que es lo que los reportes por rango necesitan.
        assertThat(fecha).isNotNull();
        assertThat(fecha.toLocalDateTime().toLocalDate().toString()).isEqualTo("2026-08-29");
    }

    @Test
    void lasDosFechaHoraQuedanComoTimestampYNoComoDate() {
        // Se mira el TIPO de la columna y no el valor, porque es lo unico que
        // mira ddl-auto=validate: una columna DATE con la fecha correcta igual
        // impide que la aplicacion arranque.
        //
        // Esto no es hipotetico. El 01/09/2026 la version instalada de agosto
        // (ddl-auto=update, fechaHora como LocalDate) arranco contra una base ya
        // migrada y bajo las dos columnas a DATE; la version nueva dejo de
        // arrancar con "wrong column type encountered in column [fecha_hora] in
        // table [compra]". El test anterior no lo habria detectado: miraba venta
        // y miraba el valor.
        assertThat(tipoDeColumna("VENTA", "FECHA_HORA")).startsWith("TIMESTAMP");
        assertThat(tipoDeColumna("COMPRA", "FECHA_HORA")).startsWith("TIMESTAMP");
    }

    private String tipoDeColumna(String tabla, String columna) {
        return jdbc.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_name = ? and column_name = ?",
                String.class, tabla, columna);
    }

    @Test
    void laCompraQueYaEstabaConservaSuDetalleYAceptaAnulacion() {
        assertThat(jdbc.queryForObject("select proveedor from compra where id = 1", String.class))
                .isEqualTo("Cocacola andina");
        // precio_costo_anterior es lo que permite anular una compra devolviendo
        // el costo previo. En las compras viejas no hay a que volver: queda null.
        assertThat(jdbc.queryForObject(
                "select precio_costo_anterior from detalle_compra where id = 1", Double.class))
                .isNull();
    }

    @Test
    void v3BackfilleaElTotalDeLasComprasQueYaExistian() {
        // Esta base es anterior a que existiera compra.total (V3). El backfill
        // tiene que sumar sus detalles una sola vez al migrar, no dejarlo en 0.
        assertThat(jdbc.queryForObject("select total from compra where id = 1", Double.class))
                .isEqualTo(40000.0);
    }
}
