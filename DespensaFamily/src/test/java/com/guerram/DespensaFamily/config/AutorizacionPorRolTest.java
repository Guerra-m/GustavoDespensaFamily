package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Quien puede hacer que.
 *
 * <p>Hasta la auditoria del 01/09/2026 no habia ninguna regla por rol fuera de
 * /reportes, /admin, /configuracion y /promociones: cualquier usuario
 * autenticado podia crear y borrar productos, registrar compras que reescriben
 * precios, y —lo mas grave— <b>anular sus propias ventas</b>. Reproducido: un
 * empleado registraba una venta de $15.000, se quedaba con la plata, la anulaba
 * el mismo y la venta desaparecia del listado sin dejar rastro.
 *
 * <p>El criterio: el empleado esta para vender y consultar. Todo lo que toca
 * plata hacia atras (anular), el catalogo, los costos o la configuracion es del
 * dueno.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class AutorizacionPorRolTest {

    @Autowired
    private MockMvc mockMvc;

    // El rol se adjunta a cada peticion con un post-processor y no con
    // @WithMockUser: la sesion es STATELESS, asi que el filtro de contexto
    // descarta lo que @WithMockUser deja en el hilo y todas las peticiones
    // terminarian corriendo como anonimas — con lo cual los casos de
    // "prohibido" darian 403 por falta de sesion y no por falta de permiso,
    // que es un test que pasa sin probar nada.
    private static final RequestPostProcessor EMPLEADO = user("empleado").roles("EMPLEADO");
    private static final RequestPostProcessor ADMIN = user("duenio").roles("ADMIN");

    /**
     * Lo unico que importa es que NO sea 403. Un 400 o un 404 significan que
     * la peticion paso el control de acceso y murio despues por otra razon
     * (payload de prueba, id inexistente), que es exactamente lo que se quiere
     * comprobar acá.
     */
    private void permitido(MvcResult resultado, String queEs) throws Exception {
        assertNotEquals(403, resultado.getResponse().getStatus(), queEs + " tendria que estar permitido");
    }

    private void prohibido(MvcResult resultado, String queEs) throws Exception {
        assertEquals(403, resultado.getResponse().getStatus(), queEs + " tendria que estar prohibido");
    }

    // ---------------------------------------------------------------- EMPLEADO

    @Test
    void elEmpleadoPuedeVenderYConsultar() throws Exception {
        permitido(mockMvc.perform(get("/productos").with(EMPLEADO)).andReturn(), "ver productos");
        permitido(mockMvc.perform(get("/categorias").with(EMPLEADO)).andReturn(), "ver categorias");
        permitido(mockMvc.perform(get("/ventas").with(EMPLEADO)).andReturn(), "ver ventas");
        permitido(mockMvc.perform(get("/compras").with(EMPLEADO)).andReturn(), "ver compras");
        permitido(mockMvc.perform(post("/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"detalles\":[]}").with(EMPLEADO)).andReturn(), "registrar una venta");
    }

    @Test
    void elEmpleadoVeElAvisoDeActualizacionPeroNoPuedeAplicarla() throws Exception {
        // Ver el aviso si: si la caja se va a reiniciar, el que esta atendiendo
        // tiene que poder entender por que. Aplicarla no: apaga el servidor.
        permitido(mockMvc.perform(get("/actualizaciones").with(EMPLEADO)).andReturn(),
                "ver si hay una actualizacion");
        prohibido(mockMvc.perform(post("/actualizaciones/aplicar").with(EMPLEADO)).andReturn(),
                "aplicar una actualizacion");
    }

    @Test
    void elEmpleadoNoPuedeAnularUnaVenta() throws Exception {
        // El agujero mas caro de todos: vender, cobrar y borrar el registro.
        prohibido(mockMvc.perform(delete("/ventas/1").with(EMPLEADO)).andReturn(), "anular una venta");
    }

    @Test
    void elEmpleadoNoPuedeTocarElCatalogo() throws Exception {
        prohibido(mockMvc.perform(post("/productos").with(EMPLEADO)).andReturn(), "crear un producto");
        prohibido(mockMvc.perform(delete("/productos/1").with(EMPLEADO)).andReturn(), "borrar un producto");
        prohibido(mockMvc.perform(post("/categorias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"X\"}").with(EMPLEADO)).andReturn(), "crear una categoria");
        prohibido(mockMvc.perform(delete("/categorias/1").with(EMPLEADO)).andReturn(), "borrar una categoria");
    }

    @Test
    void elEmpleadoSinPermisoNoVeNadaDeCaja() throws Exception {
        // Esta regla CAMBIO el 02/09/2026, y el test cambio con ella.
        //
        // Antes decia que el empleado veia el saldo y los ultimos movimientos,
        // con el argumento de que el que atiende necesita saber con que
        // efectivo cuenta para dar vuelto. Martin lo probo en la aplicacion y
        // lo marco como bug: "aunque el empleado pepe no tenga permiso de
        // caja, puede ver la caja". Manda el dueño, y ademas tiene razon de
        // fondo — el permiso se llama PERM_CAJA, "puede ver caja": si no se lo
        // prendieron, no la ve. Para vender no hace falta, la pantalla de
        // Ventas no consulta caja.
        //
        // El caso con el permiso prendido vive en PermisosGranularesTest.
        prohibido(mockMvc.perform(get("/caja").with(EMPLEADO)).andReturn(), "ver el saldo de caja sin permiso");
        prohibido(mockMvc.perform(get("/caja/ultimos").with(EMPLEADO)).andReturn(), "ver los ultimos movimientos sin permiso");
        prohibido(mockMvc.perform(get("/caja/movimientos?desde=2026-01-01&hasta=2026-01-01").with(EMPLEADO))
                .andReturn(), "ver el historial completo de caja");
        prohibido(mockMvc.perform(post("/caja/movimientos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"EGRESO\",\"categoria\":\"RETIRO\",\"monto\":1000,"
                        + "\"metodoPago\":\"EFECTIVO\",\"descripcion\":\"x\"}").with(EMPLEADO))
                .andReturn(), "cargar un movimiento de caja");
        prohibido(mockMvc.perform(post("/caja/movimientos/1/revertir").with(EMPLEADO))
                .andReturn(), "revertir un movimiento de caja");
        prohibido(mockMvc.perform(post("/caja/arqueo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"efectivoContado\":1000}").with(EMPLEADO))
                .andReturn(), "hacer un arqueo");
    }

    @Test
    void elEmpleadoNoPuedeVerNiTocarEmpleados() throws Exception {
        // Es informacion salarial del negocio: todo del dueño.
        prohibido(mockMvc.perform(get("/empleados").with(EMPLEADO)).andReturn(), "listar empleados");
        prohibido(mockMvc.perform(post("/empleados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"X\"}").with(EMPLEADO)).andReturn(), "crear un empleado");
        prohibido(mockMvc.perform(post("/empleados/1/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monto\":1000,\"metodoPago\":\"EFECTIVO\"}").with(EMPLEADO))
                .andReturn(), "pagarle a un empleado");
    }

    @Test
    void elEmpleadoNoPuedeRegistrarCompras() throws Exception {
        // Una compra reescribe el costo y el precio de venta del producto.
        prohibido(mockMvc.perform(post("/compras")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"proveedor\":\"X\",\"detalles\":[]}").with(EMPLEADO)).andReturn(), "registrar una compra");
    }

    @Test
    void elEmpleadoNoPuedeGastarLaCuotaDeIaDelDueno() throws Exception {
        prohibido(mockMvc.perform(post("/ia/productos/extraer").with(EMPLEADO)).andReturn(), "usar el asistente de IA");
    }

    @Test
    void elEmpleadoNoPuedeVerReportesNiTocarLaConfiguracion() throws Exception {
        prohibido(mockMvc.perform(get("/reportes/resumen?periodo=hoy").with(EMPLEADO)).andReturn(), "ver reportes");
        prohibido(mockMvc.perform(get("/admin/usuarios").with(EMPLEADO)).andReturn(), "listar usuarios");
        prohibido(mockMvc.perform(put("/configuracion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}").with(EMPLEADO)).andReturn(), "cambiar la configuracion");
        prohibido(mockMvc.perform(post("/configuracion/aplicar-ganancia-global")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"porcentaje\":50}").with(EMPLEADO)).andReturn(), "aplicar ganancia global");
    }

    // ------------------------------------------------------------------- ADMIN

    @Test
    void elAdminPuedeHacerTodoLoAnterior() throws Exception {
        permitido(mockMvc.perform(delete("/ventas/999999").with(ADMIN)).andReturn(), "anular una venta");
        permitido(mockMvc.perform(delete("/productos/999999").with(ADMIN)).andReturn(), "borrar un producto");
        permitido(mockMvc.perform(get("/reportes/resumen?periodo=hoy").with(ADMIN)).andReturn(), "ver reportes");
        permitido(mockMvc.perform(get("/admin/usuarios").with(ADMIN)).andReturn(), "listar usuarios");
        permitido(mockMvc.perform(post("/compras")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"proveedor\":\"X\",\"detalles\":[]}").with(ADMIN)).andReturn(), "registrar una compra");
    }

    // ------------------------------------------------------------------ ANONIMO

    @Test
    void sinSesionNoSeLlegaANingunLado() throws Exception {
        assertNotEquals(200, mockMvc.perform(get("/productos")).andReturn().getResponse().getStatus());
        assertNotEquals(200, mockMvc.perform(get("/ventas")).andReturn().getResponse().getStatus());
        assertNotEquals(200, mockMvc.perform(get("/configuracion")).andReturn().getResponse().getStatus());
    }
}
