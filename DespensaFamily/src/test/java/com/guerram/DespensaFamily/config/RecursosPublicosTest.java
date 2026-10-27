package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Lo que la pantalla de login necesita ANTES de que haya sesion.
 *
 * <p>Los iconos de la pantalla de login ({@code /icons/package.svg},
 * {@code piggy-bank.svg}, {@code users.svg}) daban 403 sin sesion: la lista de
 * rutas publicas tenia {@code /assets/**} pero no {@code /icons/**}, que es
 * donde Vite copia {@code FrontDespensaFamily/public/icons}. Son SVG estaticos
 * del propio frontend, sin datos del negocio.
 *
 * <p>En los tests no esta compilado el frontend, asi que el archivo no existe:
 * un 404 significa que paso el control de acceso, que es lo que se prueba.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class RecursosPublicosTest {

    @Autowired
    private MockMvc mockMvc;

    private int estadoSinSesion(String ruta) throws Exception {
        return mockMvc.perform(get(ruta)).andReturn().getResponse().getStatus();
    }

    @Test
    void losIconosDelLoginSeVenSinSesion() throws Exception {
        assertThat(estadoSinSesion("/icons/package.svg")).isNotEqualTo(403);
        assertThat(estadoSinSesion("/icons/piggy-bank.svg")).isNotEqualTo(403);
        assertThat(estadoSinSesion("/icons/users.svg")).isNotEqualTo(403);
    }

    @Test
    void abrirLosIconosNoAbreLasFotosDeProductos() throws Exception {
        // Las fotos si son datos del negocio y siguen pidiendo sesion.
        assertThat(estadoSinSesion("/imagenes/package.svg")).isEqualTo(403);
    }
}
