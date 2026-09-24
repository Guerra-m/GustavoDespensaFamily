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
                        // La sesión viaja en una cookie HttpOnly, así que en
                        // desarrollo (front en 5173, back en 8080) el navegador
                        // solo la manda si el servidor acepta credenciales.
                        // Es seguro porque los orígenes de arriba son una lista
                        // explícita: allowCredentials(true) con "*" lo rechaza
                        // el propio navegador, y con razón (API-06).
                        .allowCredentials(true);
            }
        };
    }
}
