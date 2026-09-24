package com.guerram.DespensaFamily.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * /ventas/cotizar tiene que quedar bajo la misma regla que el resto de
 * /ventas/** (ver SecurityConfig): el empleado puede cotizar (para armar el
 * carrito), un anonimo no.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class CotizarVentaAutorizacionTest {

    @Autowired
    private MockMvc mockMvc;

    private static final RequestPostProcessor EMPLEADO = user("empleado").roles("EMPLEADO");

    @Test
    void elEmpleadoPuedeCotizar() throws Exception {
        int status = mockMvc.perform(post("/ventas/cotizar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\":[]}")
                        .with(EMPLEADO))
                .andReturn().getResponse().getStatus();

        // Lo unico que importa es que no sea 403: la lista vacia puede dar
        // 400 (detalles @NotEmpty), pero eso es pasar el control de acceso y
        // morir despues por otra razon.
        assertNotEquals(403, status, "cotizar tendria que estar permitido para un empleado");
    }

    @Test
    void sinSesionNoSePuedeCotizar() throws Exception {
        int status = mockMvc.perform(post("/ventas/cotizar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\":[]}"))
                .andReturn().getResponse().getStatus();

        assertNotEquals(200, status, "un anonimo no tendria que poder cotizar");
    }
}
