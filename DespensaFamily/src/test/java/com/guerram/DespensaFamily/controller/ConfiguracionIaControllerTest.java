package com.guerram.DespensaFamily.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /configuracion/ia solo informa si esta configurada y de donde sale —
 * nunca la clave, ni siquiera enmascarada (SEC-01/SEC-05). No se prueba PUT
 * ni DELETE aca: escribirian sobre el config.properties real de esta
 * maquina (ver ConfiguracionIaServiceTest, que cubre esos casos contra un
 * archivo temporal).
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class ConfiguracionIaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final RequestPostProcessor ADMIN = user("duenio").roles("ADMIN");

    @Test
    void elEstadoNuncaIncluyeLaClave() throws Exception {
        mockMvc.perform(get("/configuracion/ia").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurada").exists())
                .andExpect(jsonPath("$.origen").exists())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.claveIa").doesNotExist());
    }
}
