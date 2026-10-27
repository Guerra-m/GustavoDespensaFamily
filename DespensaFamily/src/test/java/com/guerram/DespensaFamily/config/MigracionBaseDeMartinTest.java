package com.guerram.DespensaFamily.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El caso que dejo la aplicacion sin arrancar: una base que ya tiene datos del
 * negocio y a la que le falta usuario.debe_cambiar_password.
 *
 * Que el contexto levante no es un detalle del test, es la mitad de lo que se
 * esta probando: con ddl-auto=validate, Hibernate compara el modelo contra la
 * base y se niega a arrancar si no coinciden. Si la migracion no hace su
 * trabajo, este test no falla en un assert: falla al construir el contexto,
 * igual que fallaba iniciar.bat.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false",
        "spring.datasource.url=jdbc:h2:mem:base-de-martin;DB_CLOSE_DELAY=-1"
})
class MigracionBaseDeMartinTest {

    // El bloque estatico corre al cargarse la clase, o sea antes de que Spring
    // levante el contexto: cuando Flyway abre la base, el esquema viejo ya esta.
    static {
        EsquemaDePrueba.sembrar(
                "jdbc:h2:mem:base-de-martin;DB_CLOSE_DELAY=-1", "/db/esquema-antes-de-flyway.sql");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void agregaLaColumnaQueFaltabaSinMolestarAlQueYaEligioSuPassword() {
        Boolean debeCambiar = jdbc.queryForObject(
                "select debe_cambiar_password from usuario where username = 'gustavo'", Boolean.class);

        // false y no null: la columna es NOT NULL, y a quien ya eligio su
        // password no hay que pedirle nada solo porque actualizamos el sistema.
        assertThat(debeCambiar).isFalse();
    }

    @Test
    void alQueSigueConLaPasswordPublicaLeSigueExigiendoCambiarla() {
        // El default de la migracion es false, pero no puede tapar la proteccion
        // del 01/09: el admin que todavia usa 'admin123' tiene que quedar
        // marcado igual. Es la diferencia entre agregar una columna y borrar una
        // regla de seguridad sin darse cuenta.
        Boolean debeCambiar = jdbc.queryForObject(
                "select debe_cambiar_password from usuario where username = 'admin'", Boolean.class);

        assertThat(debeCambiar).isTrue();
    }

    @Test
    void conservaLosDatosDelNegocio() {
        assertThat(jdbc.queryForObject("select nombre from producto where id = 1", String.class))
                .isEqualTo("Coca-cola 2.5lts");
        assertThat(jdbc.queryForObject("select stock from producto where id = 1", Double.class))
                .isEqualTo(49.0);
        assertThat(jdbc.queryForObject("select count(*) from venta", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void noLePisaLaPasswordAlAdminQueYaExistia() {
        assertThat(jdbc.queryForObject("select password from usuario where username = 'admin'", String.class))
                .isEqualTo("$2a$10$EWroX4e/w3IyFMjEPN1DOeQBhpu0YLqTN2xfMc/kJLwimkXE/43oO");
    }
}
