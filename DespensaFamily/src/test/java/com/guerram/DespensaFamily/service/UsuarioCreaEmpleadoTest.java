package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.guerram.DespensaFamily.dto.empleados.EmpleadoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Crear un usuario tiene que dejarlo disponible en Empleados, sin cargarlo
 * dos veces.
 *
 * <p>Encontrado el 01/09/2026: se creo un usuario con rol EMPLEADO desde
 * Usuarios esperando verlo en la pantalla nueva de Empleados (dias
 * trabajados y pago), y no aparecia — las dos pantallas quedaban
 * desconectadas. Ver EmpleadoService.vincularConUsuario.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class UsuarioCreaEmpleadoTest {

    // .with(user(...)) y no @WithMockUser: la sesion es STATELESS, y el
    // filtro de contexto descarta lo que @WithMockUser deja en el hilo —
    // mismo motivo documentado en AutorizacionPorRolTest.
    private static final RequestPostProcessor ADMIN = user("duenio-de-prueba").roles("ADMIN");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpleadoService empleadoService;

    @Test
    void crearUnUsuarioLoDejaDisponibleEnEmpleados() throws Exception {
        mockMvc.perform(post("/admin/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"juanp\",\"password\":\"unaPasswordLarga123\","
                        + "\"nombre\":\"Juan Garcia\",\"rol\":\"EMPLEADO\"}")
                .with(ADMIN))
                .andReturn();

        boolean aparece = empleadoService.listar().stream()
                .anyMatch(e -> "juanp".equals(e.getUsername()) && e.isActivo());

        assertThat(aparece).isTrue();
    }

    @Test
    void noSeDuplicaSiYaExistiaLaFichaDeEmpleado() throws Exception {
        // Alguien ya habia creado a mano el Empleado "Preexistente"/preex,
        // por ejemplo desde la pantalla de Empleados directamente.
        var manual = crearEmpleadoManual("preex", "Preexistente");

        mockMvc.perform(post("/admin/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"preex\",\"password\":\"unaPasswordLarga123\","
                        + "\"nombre\":\"Nombre Distinto\",\"rol\":\"EMPLEADO\"}")
                .with(ADMIN))
                .andReturn();

        long cuantos = empleadoService.listar().stream()
                .filter(e -> "preex".equals(e.getUsername()))
                .count();

        assertThat(cuantos).isEqualTo(1);
        // Y no se le piso el nombre que ya tenia cargado.
        assertThat(empleadoService.listar().stream()
                .filter(e -> "preex".equals(e.getUsername()))
                .findFirst().orElseThrow().getNombre())
                .isEqualTo(manual.getNombre());
    }

    private EmpleadoDTO crearEmpleadoManual(String username, String nombre) {
        var dto = new com.guerram.DespensaFamily.dto.empleados.CrearEmpleadoRequestDTO();
        dto.setNombre(nombre);
        dto.setUsername(username);
        return empleadoService.crear(dto);
    }
}
