package com.guerram.DespensaFamily.actualizacion;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionDeActualizaciones {

    /**
     * La version que declara el pom, grabada dentro del jar por el goal
     * build-info del plugin de Spring Boot.
     *
     * <p>Se pide con ObjectProvider y no directo porque hay un caso donde no
     * existe: compilar sin pasar por el plugin (un run desde el IDE recien
     * clonado, por ejemplo). Ahi la version queda en 0.0.0, que no es posterior
     * a nada, asi que el efecto es que no se ofrece ninguna actualizacion. Es la
     * respuesta correcta: si no sabemos que version somos, no podemos decidir si
     * la de internet es mas nueva.
     */
    private static final String VERSION_DESCONOCIDA = "0.0.0";

    @Bean
    ServicioDeActualizaciones servicioDeActualizaciones(
            @Value("${despensa.actualizaciones}") String carpeta,
            ObjectProvider<BuildProperties> build) {

        String version = build.getIfAvailable() != null
                ? build.getObject().getVersion()
                : VERSION_DESCONOCIDA;

        Path ruta = Paths.get(carpeta);
        return new ServicioDeActualizaciones(ruta, version);
    }
}
