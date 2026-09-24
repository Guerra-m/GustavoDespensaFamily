package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DF-BUG-006: un pedido mal armado tiene que volver como 400 con un mensaje en
 * castellano que la pantalla pueda mostrar.
 *
 * <p>Antes: un body que no pasaba {@code @Valid} o un parametro obligatorio
 * que faltaba devolvian <b>403 vacio</b> — Spring mandaba el error a
 * {@code /error}, que no estaba permitido, y el pedido rebotaba como si fuera
 * un problema de permisos. Un JSON roto o un enum desconocido (BITCOIN como
 * metodo de pago) devolvian el mensaje crudo de Jackson, con el nombre
 * completo de la clase {@code com.guerram...MetodoPago} adentro (API-08).
 *
 * <p>El frontend lee el campo {@code error} del cuerpo: se mantiene.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class ErroresLegiblesTest {

    @Autowired
    private MockMvc mockMvc;

    private static final RequestPostProcessor ADMIN = user("duenio").roles("ADMIN");

    /** Nada de nombres de paquete ni de clase internos en la respuesta. */
    private static void sinDetallesInternos(MvcResult resultado) throws Exception {
        String cuerpo = resultado.getResponse().getContentAsString();
        assertThat(cuerpo)
                .doesNotContain("com.guerram")
                .doesNotContain("tools.jackson")
                .doesNotContain("java.lang")
                .doesNotContain("java.time");
    }

    // ------------------------------------------------------ @Valid (body)

    @Test
    void crearUsuarioVacioDa400ConElCampoQueFalta() throws Exception {
        MvcResult r = mockMvc.perform(post("/admin/usuarios").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos inválidos"))
                .andReturn();

        assertThat(r.getResponse().getContentAsString())
                .contains("username")
                .contains("obligatorio");
        sinDetallesInternos(r);
    }

    @Test
    void ventaSinMetodoDePagoDa400ConElMensajeDelDto() throws Exception {
        MvcResult r = mockMvc.perform(post("/ventas").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\":[{\"productoId\":1,\"cantidad\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos inválidos"))
                .andReturn();

        // El DTO trae su propio mensaje en castellano: se respeta tal cual.
        assertThat(r.getResponse().getContentAsString()).contains("El método de pago es obligatorio");
    }

    // ------------------------------------------------ JSON / enum invalidos

    @Test
    void unMetodoDePagoInexistenteDa400SinNombresDeClase() throws Exception {
        MvcResult r = mockMvc.perform(post("/ventas").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\":[{\"productoId\":1,\"cantidad\":1}],\"metodoPago\":\"BITCOIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andReturn();

        sinDetallesInternos(r);
        // Le dice al que llama que valor no sirvio y cuales si.
        assertThat(r.getResponse().getContentAsString())
                .contains("BITCOIN")
                .contains("EFECTIVO");
    }

    @Test
    void unJsonRotoDa400SinElMensajeDeJackson() throws Exception {
        MvcResult r = mockMvc.perform(post("/ventas").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\": [ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos inválidos"))
                .andExpect(jsonPath("$.detalle").value("El cuerpo del pedido no es un JSON válido"))
                .andReturn();

        sinDetallesInternos(r);
    }

    // ------------------------------------------------- parametros de la URL

    @Test
    void unParametroObligatorioQueFaltaDa400ConSuNombre() throws Exception {
        MvcResult r = mockMvc.perform(get("/reportes/productos-mas-vendidos").with(ADMIN)
                        .param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andReturn();

        assertThat(r.getResponse().getContentAsString()).contains("desde");
        sinDetallesInternos(r);
    }

    @Test
    void unParametroConFormatoInvalidoDa400SinNombresDeClase() throws Exception {
        MvcResult r = mockMvc.perform(get("/reportes/productos-mas-vendidos").with(ADMIN)
                        .param("desde", "ayer")
                        .param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andReturn();

        assertThat(r.getResponse().getContentAsString()).contains("desde");
        sinDetallesInternos(r);
    }

    // ---------------------------------------------------------------- /error

    @Test
    void laPaginaDeErrorNoRebotaComoProhibida() throws Exception {
        // Es a donde Spring manda cualquier error que no maneje un
        // @ExceptionHandler. Si no esta permitida, el error real se pierde y
        // el cliente ve un 403 que parece de permisos.
        int estado = mockMvc.perform(get("/error")).andReturn().getResponse().getStatus();

        assertThat(estado).isNotEqualTo(403);
    }
}
