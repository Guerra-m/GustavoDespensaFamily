package com.guerram.DespensaFamily.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DF-INST-002. Una version vieja arrancando sobre una base que ya migro una mas
 * nueva la degrada: paso de verdad, la 1.0.0 bajo fecha_hora a DATE. Flyway, por
 * defecto, ignora las migraciones "futuras" (ignoreMigrationPatterns=*:future)
 * y deja seguir. Esta version se niega, con un mensaje que el usuario entiende.
 */
class ControlDeVersionDeLaBaseTest {

    private final String url = "jdbc:h2:mem:version-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }

    /** Lo que deja una version posterior: una fila de historial que esta no conoce. */
    private void unaVersionMasNuevaMigro(String version) throws Exception {
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                    "select max(\"installed_rank\") from \"flyway_schema_history\"");
            rs.next();
            int rango = rs.getInt(1) + 1;
            s.executeUpdate("insert into \"flyway_schema_history\" (\"installed_rank\", \"version\","
                    + " \"description\", \"type\", \"script\", \"checksum\", \"installed_by\","
                    + " \"execution_time\", \"success\") values (" + rango + ", '" + version
                    + "', 'de una version posterior', 'SQL', 'V" + version + "__x.sql', 1, 'sa', 1, true)");
        }
    }

    private int tablasDeVentas() throws Exception {
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             ResultSet rs = c.getMetaData().getTables(null, null, "VENTA", null)) {
            return rs.next() ? 1 : 0;
        }
    }

    @Test
    void una_base_nueva_se_migra_normalmente() throws Exception {
        new ControlDeVersionDeLaBase().migrate(flyway());

        assertThat(tablasDeVentas()).isEqualTo(1);
    }

    @Test
    void una_base_ya_al_dia_arranca_sin_problema() {
        flyway().migrate();

        new ControlDeVersionDeLaBase().migrate(flyway());
    }

    @Test
    void una_base_migrada_por_una_version_mas_nueva_no_arranca() throws Exception {
        flyway().migrate();
        unaVersionMasNuevaMigro("999");

        assertThatThrownBy(() -> new ControlDeVersionDeLaBase().migrate(flyway()))
                .isInstanceOf(BaseDeUnaVersionMasNuevaException.class)
                .hasMessageContaining("una version mas nueva de DespensaFamily")
                .hasMessageContaining("instala la ultima");
    }

    @Test
    void el_aviso_dice_hasta_que_version_de_la_base_llego() throws Exception {
        flyway().migrate();
        unaVersionMasNuevaMigro("998");
        unaVersionMasNuevaMigro("999");

        assertThatThrownBy(() -> new ControlDeVersionDeLaBase().migrate(flyway()))
                .isInstanceOf(BaseDeUnaVersionMasNuevaException.class)
                .satisfies(e -> assertThat(((BaseDeUnaVersionMasNuevaException) e).versionDeLaBase())
                        .isEqualTo("999"));
    }

    @Test
    void el_arranque_fallido_se_explica_sin_stacktrace() {
        FailureAnalysis analisis = new BaseDeUnaVersionMasNuevaFailureAnalyzer()
                .analyze(new IllegalStateException("envoltorio de Spring",
                        new BaseDeUnaVersionMasNuevaException("15", "12")));

        assertThat(analisis).isNotNull();
        assertThat(analisis.getDescription()).contains("una version mas nueva");
        assertThat(analisis.getAction()).contains("instala la ultima");
    }

    @Test
    void el_motivo_queda_escrito_para_que_lo_muestre_la_ventana(@TempDir Path logs) throws Exception {
        boolean anotado = ErrorDeArranque.anotar(
                new IllegalStateException("x", new BaseDeUnaVersionMasNuevaException("15", "12")), logs);

        assertThat(anotado).isTrue();
        assertThat(Files.readString(logs.resolve(ErrorDeArranque.ARCHIVO)))
                .contains("una version mas nueva de DespensaFamily");
    }

    @Test
    void un_error_cualquiera_no_se_disfraza_de_base_nueva(@TempDir Path logs) {
        // Para el resto de los errores la ventana ya dice donde esta el log:
        // anotar algo generico aca solo confundiria.
        boolean anotado = ErrorDeArranque.anotar(new IllegalStateException("puerto ocupado"), logs);

        assertThat(anotado).isFalse();
        assertThat(logs.resolve(ErrorDeArranque.ARCHIVO)).doesNotExist();
    }
}
