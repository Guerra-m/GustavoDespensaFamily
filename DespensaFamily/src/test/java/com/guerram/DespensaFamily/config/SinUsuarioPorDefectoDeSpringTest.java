package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DF-SEC-004: sin un {@code UserDetailsService} propio, Spring Boot arranca
 * su autoconfiguración de un usuario "user" con contraseña generada al azar
 * e IMPRESA por consola en cada arranque
 * ({@code UserDetailsServiceAutoConfiguration}, que crea un
 * {@link InMemoryUserDetailsManager}) — un usuario y una contraseña que nadie
 * pidió, en un log que cualquiera con acceso al arranque puede leer.
 *
 * <p>No hace falta un {@code UserDetailsService} acá: la autenticación real
 * es {@code JwtAuthFilter}, que resuelve el usuario directo contra
 * {@code UsuarioRepository}. El bean declarado en {@link SecurityConfig} solo
 * existe para que Spring Boot se dé por servido y no arme el suyo (la
 * autoconfiguración se desactiva sola si detecta cualquier bean de este
 * tipo) — y como no lo usa nadie, puede rechazar cualquier nombre de usuario.
 *
 * <p>Esta prueba no reinicia el contexto para leer el log de arranque (el
 * contexto de {@code @SpringBootTest} ya está cacheado y compartido con
 * {@code LoginSeguroTest} y {@code CambiarPasswordSeguroTest}, que arrancan
 * antes): en cambio, verifica el efecto directo — que NO hay un
 * {@link InMemoryUserDetailsManager} en el contexto (que es lo único que
 * podría contener el "user" generado) y que el único
 * {@code UserDetailsService} presente es el propio, que rechaza cualquier
 * nombre, incluido el "user" clásico de Spring Boot.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class SinUsuarioPorDefectoDeSpringTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    void noHayUnInMemoryUserDetailsManagerEnElContexto() {
        // Es el tipo de bean que crea UserDetailsServiceAutoConfiguration
        // para el usuario "user" generado. Si no existe ninguno, Spring Boot
        // nunca activó esa autoconfiguración.
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> contexto.getBean(InMemoryUserDetailsManager.class),
                "no puede existir un InMemoryUserDetailsManager: es la autoconfiguracion "
                        + "del usuario 'user' con contrasena generada, que este bean tiene que desactivar");
    }

    @Test
    void elUserDetailsServicePropioRechazaElUsuarioPorDefectoDeSpringBoot() {
        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("user"),
                "el nombre 'user' es el que Spring Boot le pone a su usuario generado: "
                        + "tiene que seguir sin existir ninguna cuenta con ese nombre");
    }

    @Test
    void elUserDetailsServicePropioRechazaCualquierUsuarioReal() {
        // No es un UserDetailsService funcional: nadie lo tiene que usar para
        // autenticar. Si alguna vez alguien lo conecta a un
        // DaoAuthenticationProvider por accidente, esta prueba se rompe y
        // avisa que ahora hay un segundo camino de autenticacion que no pasa
        // por JwtAuthFilter/UsuarioRepository.
        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("admin"));
    }

    @Test
    void esExactamenteElBeanDeclaradoEnSecurityConfig() {
        assertTrue(contexto.containsBean("userDetailsService"),
                "tiene que existir un bean con este nombre: es el que apaga la autoconfiguracion");
    }
}
