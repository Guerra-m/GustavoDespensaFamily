package com.guerram.DespensaFamily.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DF-SEC-001: XSS almacenado reproducido en vivo (categoria y producto con
 * {@code <img onerror=...>}) ejecutando en la sesion del dueño. Escapar en
 * el render (FE-03) es la defensa real; esta cabecera es la segunda capa
 * (HDR-01). Arranca en Report-Only: mide sin romper nada mientras el
 * inventario de sinks (ver reporte del cambio) se termina de limpiar.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class CabeceraCspTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unaPaginaEstaticaTraeLaCabeceraCspEnModoReportOnly() throws Exception {
        String csp = mockMvc.perform(get("/"))
                .andReturn().getResponse().getHeader("Content-Security-Policy-Report-Only");

        assertThat(csp).isNotBlank();
        assertThat(csp).contains("default-src 'self'");
        assertThat(csp).contains("object-src 'none'");
        // Report-Only: no debe venir tambien la version que bloquea, porque
        // hoy hay estilos inline y un <script> en index.html que romperian.
        String enforced = mockMvc.perform(get("/"))
                .andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(enforced).isNull();
    }

    @Test
    void unaRespuestaDeApiTambienTraeLaCabeceraCsp() throws Exception {
        // No hace falta sesion: alcanza con ver que el filtro de cabeceras de
        // Spring Security corre para cualquier respuesta, no solo estaticos.
        String csp = mockMvc.perform(get("/usuario/login"))
                .andReturn().getResponse().getHeader("Content-Security-Policy-Report-Only");

        assertThat(csp).isNotBlank();
    }
}
