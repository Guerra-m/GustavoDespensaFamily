package com.guerram.DespensaFamily.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:3000",
                                "http://192.168.100.6:3000",
                                "http://localhost:5173",
                                "http://192.168.100.6:5173"
                        )
                        // Faltaba PATCH: /usuario/{id}/password y
                        // /productos/{id}/stock-precio son PATCH reales y el
                        // navegador los bloqueaba en preflight.
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        // La auth es Bearer token en localStorage, no cookies
                        // (confirmado: ningún fetch del frontend usa
                        // credentials:"include") — allowCredentials(true) solo
                        // ampliaba la superficie sin necesidad (API-06).
                        .allowCredentials(false);
            }
        };
    }
}
