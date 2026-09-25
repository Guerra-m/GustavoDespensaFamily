package com.guerram.DespensaFamily.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DF-PERF-002 de la auditoria del 23/09: V16 agrega un indice compuesto
 * sobre venta(fecha_hora, estado), que es exactamente el filtro que usan
 * todos los reportes de rango de fechas. Se corre contra el arranque real de
 * la app (misma base que usa toda la suite), no una copia -- si V16 no
 * corriera o el nombre de columnas cambiara, este test lo detecta.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class MigracionIndiceVentaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void elIndiceCompuestoDeFechaYEstadoExisteSobreVenta() {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT COLUMN_NAME, ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.INDEXES
                WHERE TABLE_NAME = 'VENTA' AND INDEX_NAME = 'IX_VENTA_FECHA_HORA_ESTADO'
                ORDER BY ORDINAL_POSITION
                """);

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).get("COLUMN_NAME")).isEqualTo("FECHA_HORA");
        assertThat(filas.get(1).get("COLUMN_NAME")).isEqualTo("ESTADO");
    }
}
