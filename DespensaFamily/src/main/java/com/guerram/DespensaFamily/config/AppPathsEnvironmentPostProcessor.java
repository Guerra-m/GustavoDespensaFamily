package com.guerram.DespensaFamily.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Prepara la instalacion antes de que Spring cree ningun bean:
 * crea la carpeta de datos, publica ${despensa.*} y garantiza que exista un
 * secreto de firma propio de esta PC.
 *
 * <p>No alcanza con hacerlo en {@code main()} ni en un bloque estatico de la
 * clase de aplicacion: los tests levantan el contexto sin pasar por main, y la
 * inicializacion estatica de esa clase recien corre cuando Spring la instancia
 * como bean — para entonces la URL de la base ya se intento resolver.
 */
public class AppPathsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Si la configuracion ya dice donde van los datos (el application.properties
        // de los tests, o DESPENSA_HOME, que Spring lee como despensa.home), se
        // respeta. Antes se ignoraba y la suite escribia en la instalacion real.
        Path carpetaDeDatos = AppPaths.bootstrap(environment.getProperty(AppPaths.PROP_HOME));

        try {
            String secreto = SecretoJwt.asegurar(carpetaDeDatos.resolve("config.properties"));

            // Se agrega al final: la variable de entorno JWT_SECRET y el propio
            // config.properties siguen ganando. Esto solo asegura que la
            // propiedad siempre resuelva, incluso si el archivo no se pudo leer.
            environment.getPropertySources().addLast(
                    new MapPropertySource("secretoDeLaInstalacion", Map.of(SecretoJwt.CLAVE, secreto)));

        } catch (IOException e) {
            // Todavia no hay logging configurado en esta etapa del arranque.
            System.err.println("No se pudo preparar el secreto de firma en la carpeta de datos: "
                    + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        // Despues de cargar los application.properties, para poder leer el
        // despensa.home que definan. Sigue siendo antes de que se cree ningun
        // bean: la URL de la base todavia no se resolvio. En produccion main()
        // ya publico las rutas, asi que el spring.config.import del archivo de
        // la instalacion las encuentra igual.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
