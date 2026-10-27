package com.guerram.DespensaFamily.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
            // DF-SEC-001: XSS almacenado reproducido en vivo (categoria y
            // producto con <img onerror=...>) ejecutando en la sesion del
            // dueño. Escapar en el render (ver esc() en front) es la
            // defensa real (FE-03); esto es la segunda capa (HDR-01):
            // arranca en Report-Only para medir que no rompe nada antes de
            // hacerla cumplir — hay un <script> inline en index.html y
            // estilos inline en varias pantallas que una CSP enforced con
            // script-src/style-src estrictos rompería (ver reporte de
            // DF-SEC-001 para el detalle). style-src permite
            // 'unsafe-inline' a proposito: las pantallas usan atributos
            // style="..." para variables como --icono; no hay <script>
            // inline nuevo mas que el de index.html, ya inventariado.
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; "
                        + "script-src 'self'; "
                        + "object-src 'none'; "
                        + "base-uri 'self'; "
                        + "frame-ancestors 'none'; "
                        + "img-src 'self' data: blob:; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "connect-src 'self'")
                    .reportOnly()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 401 + cookie borrada si la sesion que traia el pedido ya no
            // vale (usuario borrado, token roto o vencido); 403 si nunca hubo
            // sesion, como antes. Ver JwtAuthFilter (DF-SEC-002).
            .exceptionHandling(e -> e.authenticationEntryPoint(new SesionInvalidaEntryPoint()))
            .authorizeHttpRequests(auth -> auth
                // --- Publico ---
                .requestMatchers("/usuario/login").permitAll()
                // Cerrar sesion es publico: si la cookie ya vencio o vino
                // rota, el pedido igual tiene que poder limpiarla en vez de
                // rebotar y dejar al usuario sin forma de sacarsela de encima.
                .requestMatchers("/usuario/logout").permitAll()
                .requestMatchers("/", "/*.html", "/assets/**", "/src/**", "/favicon.ico").permitAll()
                // Los SVG de FrontDespensaFamily/public/icons: la pantalla de
                // login los usa antes de que haya sesion, y sin esto daban 403.
                .requestMatchers("/icons/**").permitAll()

                // DF-BUG-006: es a donde Spring despacha cualquier error que
                // no resuelva un @ExceptionHandler. Cerrado, el error real se
                // perdia y el cliente veia un 403 vacio, como si fuera de
                // permisos. Solo devuelve status y un texto generico (Spring
                // Boot no incluye mensaje ni stack trace por defecto), asi que
                // abrirlo no expone nada (API-08).
                .requestMatchers("/error").permitAll()

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
                // Leerlas tambien es ver costos y proveedores (DF-SEC-003):
                // el dueño o quien gestiona productos. Antes, cualquiera.
                .requestMatchers(HttpMethod.GET, "/compras/**").hasAnyAuthority("ROLE_ADMIN", "PERM_STOCK")
                .requestMatchers("/compras/**").hasRole("ADMIN")

                // El asistente de IA gasta la cuota paga del dueno, y sirve
                // para dar de alta productos, que ya es tarea de ADMIN.
                .requestMatchers("/ia/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET, "/promociones/**").authenticated()
                .requestMatchers("/promociones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/descuentos/**").authenticated()
                .requestMatchers("/descuentos/**").hasRole("ADMIN")
                // La clave de Gemini es ADMIN incluso para GET: la regla
                // general de abajo deja el GET de /configuracion/** abierto
                // a cualquier autenticado (ganancia, nombre del negocio,
                // etc.), pero saber si el asistente de IA esta configurado
                // no tiene por que verlo un empleado.
                .requestMatchers("/configuracion/ia").hasRole("ADMIN")
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

    /**
     * DF-SEC-004: sin este bean, Spring Boot arranca su propio
     * {@code UserDetailsService} en memoria — un usuario "user" con una
     * contraseña generada al azar que además IMPRIME por consola en cada
     * arranque ("Using generated security password..."). No lo usa nadie:
     * la autenticacion real es JwtAuthFilter, que resuelve el usuario
     * directo contra UsuarioRepository, no contra un UserDetailsService.
     * Declarar este bean (aunque no haga nada mas que rechazar todo) le
     * dice a Spring Boot que ya hay uno y apaga esa autoconfiguracion
     * (UserDetailsServiceAutoConfiguration se auto-desactiva si detecta un
     * bean de este tipo) — un usuario y contraseña que nadie pidio, en un
     * log que cualquiera con acceso al arranque puede leer.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(
                    "Este proyecto no usa UserDetailsService: la autenticacion es via JwtAuthFilter contra UsuarioRepository.");
        };
    }
}
