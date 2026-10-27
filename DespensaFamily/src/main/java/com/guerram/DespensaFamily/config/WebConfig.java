package com.guerram.DespensaFamily.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Antes esto era "file:/C:/uploads/productos/" hardcodeado: escribia en la
    // raiz de C: (que en una PC con permisos normales puede fallar) y quedaba
    // fuera de la carpeta de datos, asi que ni el respaldo ni el desinstalador
    // lo tenian en cuenta.
    @Value("${despensa.imagenes}")
    private String carpetaImagenes;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/imagenes/**")
                .addResourceLocations("file:" + carpetaImagenes + "/");
    }
}
