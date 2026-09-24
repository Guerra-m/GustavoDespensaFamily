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
    private final FiltroDeOrigen filtroDeOrigen;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // --- Publico ---
                .requestMatchers("/usuario/login").permitAll()
                // Cerrar sesion es publico: si la cookie ya vencio o vino
                // rota, el pedido igual tiene que poder limpiarla en vez de
                // rebotar y dejar al usuario sin forma de sacarsela de encima.
                .requestMatchers("/usuario/logout").permitAll()
                .requestMatchers("/", "/*.html", "/assets/**", "/src/**", "/favicon.ico").permitAll()

                // El celular que escanea no tiene sesión propia (ver
                // EscaneoService) — solo puede publicar un código dentro de
                // una sesión que ya generó un usuario autenticado desde
                // Ventas, y esa sesión vence sola a los 30 minutos.
                .requestMatchers(HttpMethod.POST, "/escaneo/sesiones/*/codigos").permitAll()

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

                // Catalogo: mirar cualquiera. Tocar (crear/editar/eliminar
                // productos y categorias) el dueño, o el empleado al que se
                // le prendió el permiso PERM_STOCK — ver Usuario.java y
                // Usuarios (pantalla) para activarlo por persona.
                .requestMatchers(HttpMethod.GET, "/productos/**").authenticated()
                .requestMatchers("/productos/**").hasAnyAuthority("ROLE_ADMIN", "PERM_STOCK")
                .requestMatchers(HttpMethod.GET, "/categorias/**").authenticated()
                .requestMatchers("/categorias/**").hasAnyAuthority("ROLE_ADMIN", "PERM_STOCK")

                // Compras: una compra reescribe costo y precio de venta.
                .requestMatchers(HttpMethod.GET, "/compras/**").authenticated()
                .requestMatchers("/compras/**").hasRole("ADMIN")

                // El asistente de IA gasta la cuota paga del dueno, y sirve
                // para dar de alta productos, que ya es tarea de ADMIN.
                .requestMatchers("/ia/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET, "/promociones/**").authenticated()
                .requestMatchers("/promociones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/descuentos/**").authenticated()
                .requestMatchers("/descuentos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/configuracion/**").authenticated()
                .requestMatchers("/configuracion/**").hasRole("ADMIN")
                .requestMatchers("/reportes/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // Caja: nada de la caja se ve sin permiso.
                //
                // Antes el saldo y los ultimos movimientos eran para cualquier
                // autenticado, con el argumento de que el que atiende necesita
                // saber con que efectivo cuenta para dar vuelto. Martin lo
                // reporto mirando la aplicacion el 02/09/2026: "aunque el
                // empleado pepe no tenga permiso de caja, puede ver la caja".
                // Y tiene razon — el permiso se llama PERM_CAJA, "puede ver
                // caja": si el dueño no se lo prendio a alguien, esa persona
                // no tiene por que ver cuanta plata hay en el negocio. Para
                // vender no hace falta: la pantalla de Ventas no consulta caja.
                .requestMatchers(HttpMethod.GET, "/caja", "/caja/ultimos")
                        .hasAnyAuthority("ROLE_ADMIN", "PERM_CAJA")
                // El listado historico (con gastos y retiros) lo ve el dueño,
                // o el empleado con PERM_CAJA prendido ("cajero" — ver
                // Usuario.java). Cargar, revertir o arquear sigue siendo
                // siempre del dueño: es plata que se mueve, no solo mirar.
                .requestMatchers(HttpMethod.GET, "/caja/movimientos").hasAnyAuthority("ROLE_ADMIN", "PERM_CAJA")
                .requestMatchers("/caja/**").hasRole("ADMIN")

                // Empleados: pagos y dias trabajados. Todo del dueño — es
                // informacion salarial del negocio, igual de sensible que
                // gastos y retiros.
                .requestMatchers("/empleados/**").hasRole("ADMIN")

                // Actualizaciones: el aviso lo ve cualquiera que este adentro
                // —para que el empleado entienda por que la caja se va a
                // reiniciar—, pero aplicarla apaga el servidor, asi que decide
                // el dueno. Ver ActualizacionesController.
                .requestMatchers(HttpMethod.GET, "/actualizaciones").authenticated()
                .requestMatchers("/actualizaciones/**").hasRole("ADMIN")

                // Las fotos de producto ya no son publicas: se ven con sesion.
                // Antes cualquiera en el wifi del local podia recorrer el
                // catalogo de imagenes sin siquiera loguearse.
                .requestMatchers("/imagenes/**").authenticated()

                .anyRequest().authenticated()
            )
            // El filtro de origen va ANTES del de sesion: si el pedido viene
            // de otro sitio y modifica datos, se corta sin siquiera mirar quien
            // es. Ver FiltroDeOrigen (SESS-10).
            .addFilterBefore(filtroDeOrigen, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
