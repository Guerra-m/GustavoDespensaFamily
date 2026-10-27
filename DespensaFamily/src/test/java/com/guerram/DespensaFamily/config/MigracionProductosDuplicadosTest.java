package com.guerram.DespensaFamily.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guerram.DespensaFamily.DespensaFamilyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * DF-BUG-009, caso limite decidido a proposito: si una base YA tenia dos
 * productos con el mismo codigo de barras antes de actualizar (no es el caso
 * de la base real de Martin, pero otra base podria tenerlo), V14 no elige en
 * silencio cual se queda con el codigo -- el arranque falla fuerte, con el
 * error de H2/Flyway nombrando el indice y el valor repetido.
 *
 * A diferencia de MigracionProductosTest, este NO usa @SpringBootTest: lo que
 * se prueba es justamente que el contexto NO llega a levantar, asi que hay
 * que armarlo a mano para poder atrapar la excepcion.
 */
class MigracionProductosDuplicadosTest {

    @Test
    void elArranqueFallaSiYaHabiaDosProductosConElMismoCodigoDeBarras() {
        String url = "jdbc:h2:mem:productos-duplicados;DB_CLOSE_DELAY=-1";
        EsquemaDePrueba.sembrar(url, "/db/productos-duplicados.sql");

        assertThatThrownBy(() -> {
            // Argumentos de linea de comandos, NO .properties(Map): esos
            // ultimos se agregan como "default properties" (prioridad MAS
            // BAJA que application.properties del classpath), asi que la
            // URL de prueba quedaba tapada por la del application.properties
            // de test (jdbc:h2:mem:tests) y el contexto arrancaba limpio
            // contra la base de siempre en vez de contra esta con duplicados
            // -- el bug real por el que este test no fallaba al principio.
            ConfigurableApplicationContext contexto = new SpringApplicationBuilder(DespensaFamilyApplication.class)
                    .run(
                            "--despensa.escritorio=false",
                            "--despensa.respaldos.al-arrancar=false",
                            "--spring.datasource.url=" + url,
                            // Puerto random: esto arranca un servidor real
                            // (no es @SpringBootTest con MOCK), y el 8080 es
                            // el de la app real -- nunca hay que pisarlo.
                            "--server.port=0"
                    );
            contexto.close();
        }).isInstanceOf(Throwable.class);
    }
}
