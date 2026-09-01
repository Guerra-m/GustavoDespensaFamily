package com.guerram.DespensaFamily.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // --- Publico ---
                .requestMatchers("/usuario/login").permitAll()
                .requestMatchers("/", "/*.html", "/assets/**", "/src/**", "/favicon.ico").permitAll()

                // --- Matriz de permisos por rol ---
                // Criterio: el empleado esta para VENDER y CONSULTAR. Todo lo
                // que toca plata hacia atras (anular), el catalogo, los costos
                // o la configuracion es del dueno.
                //
                // Antes de la auditoria del 01/09/2026 nada de esto existia:
                // cualquier autenticado creaba y borraba productos, registraba
                // compras que reescriben precios, y anulaba ventas — incluidas
                // las suyas, cobrando y borrando el registro.

                // Ventas: vender y consultar cualquiera; ANULAR solo el dueno.
                .requestMatchers(HttpMethod.DELETE, "/ventas/**").hasRole("ADMIN")
                .requestMatchers("/ventas/**").authenticated()

                // Catalogo: mirar cualquiera, tocar solo el dueno.
                .requestMatchers(HttpMethod.GET, "/productos/**").authenticated()
                .requestMatchers("/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/categorias/**").authenticated()
                .requestMatchers("/categorias/**").hasRole("ADMIN")

                // Compras: una compra reescribe costo y precio de venta.
                .requestMatchers(HttpMethod.GET, "/compras/**").authenticated()
                .requestMatchers("/compras/**").hasRole("ADMIN")

                // El asistente de IA gasta la cuota paga del dueno, y sirve
                // para dar de alta productos, que ya es tarea de ADMIN.
                .requestMatchers("/ia/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET, "/promociones/**").authenticated()
                .requestMatchers("/promociones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/configuracion/**").authenticated()
                .requestMatchers("/configuracion/**").hasRole("ADMIN")
                .requestMatchers("/reportes/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // Las fotos de producto ya no son publicas: se ven con sesion.
                // Antes cualquiera en el wifi del local podia recorrer el
                // catalogo de imagenes sin siquiera loguearse.
                .requestMatchers("/imagenes/**").authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
