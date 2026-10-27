package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DF-TEST-001: correr la suite pisaba el credenciales-iniciales.txt de la
 * instalacion real. El post-processor resolvia la carpeta de datos antes de que
 * cargara el application.properties de los tests y la publicaba como propiedad
 * de sistema, que le gana a cualquier archivo: el despensa.home de los tests
 * nunca llegaba a usarse.
 */
class AppPathsEnvironmentPostProcessorTest {

    private static final List<String> PROPIEDADES = List.of(
            AppPaths.PROP_HOME, AppPaths.PROP_DB, AppPaths.PROP_IMAGENES,
            AppPaths.PROP_BACKUPS, AppPaths.PROP_LOGS, AppPaths.PROP_ACTUALIZACIONES);

    // Son propiedades de sistema: sin devolverlas, este test le cambiaria la
    // carpeta de datos a todos los que corran despues en la misma JVM.
    private final Map<String, String> anteriores = new HashMap<>();

    @BeforeEach
    void guardarPropiedades() {
        PROPIEDADES.forEach(p -> anteriores.put(p, System.getProperty(p)));
        PROPIEDADES.forEach(System::clearProperty);
    }

    @AfterEach
    void devolverPropiedades() {
        anteriores.forEach((p, v) -> {
            if (v == null) System.clearProperty(p);
            else System.setProperty(p, v);
        });
    }

    @Test
    void corre_despues_de_cargar_los_application_properties() {
        // Si corre antes, el despensa.home del archivo todavia no existe y no
        // hay nada que respetar.
        assertTrue(new AppPathsEnvironmentPostProcessor().getOrder()
                > ConfigDataEnvironmentPostProcessor.ORDER);
    }

    @Test
    void respeta_la_carpeta_de_datos_ya_definida(@TempDir Path carpeta) {
        Path home = carpeta.resolve("datos-de-prueba");

        new AppPathsEnvironmentPostProcessor()
                .postProcessEnvironment(entornoCon(home), new SpringApplication());

        assertEquals(home.toString().replace('\\', '/'), System.getProperty(AppPaths.PROP_HOME));
        assertEquals(home.resolve("db").toString().replace('\\', '/'),
                System.getProperty(AppPaths.PROP_DB));
        // El secreto de firma se escribe ahi y no en la instalacion real.
        assertTrue(Files.isRegularFile(home.resolve("config.properties")));
    }

    @Test
    void otra_carpeta_definida_es_otra_carpeta(@TempDir Path carpeta) {
        Path home = carpeta.resolve("otra");

        new AppPathsEnvironmentPostProcessor()
                .postProcessEnvironment(entornoCon(home), new SpringApplication());

        assertEquals(home.resolve("backups").toString().replace('\\', '/'),
                System.getProperty(AppPaths.PROP_BACKUPS));
    }

    /** Un entorno sin variables del sistema: solo lo que diria un .properties. */
    private static ConfigurableEnvironment entornoCon(Path home) {
        ConfigurableEnvironment entorno = new AbstractEnvironment() {
        };
        entorno.getPropertySources().addLast(new MapPropertySource(
                "application.properties", Map.of(AppPaths.PROP_HOME, home.toString())));
        return entorno;
    }
}
