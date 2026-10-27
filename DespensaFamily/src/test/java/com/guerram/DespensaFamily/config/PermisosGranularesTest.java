package com.guerram.DespensaFamily.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.guerram.DespensaFamily.dto.PermisosUsuarioDTO;
import com.guerram.DespensaFamily.dto.UsuarioDTO;
import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import com.guerram.DespensaFamily.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Un EMPLEADO con un permiso suelto prendido (PERM_STOCK o PERM_CAJA) puede
 * entrar justo a eso, sin volverse ADMIN — ver Usuario.java,
 * JwtAuthFilter y SecurityConfig.
 *
 * <p>Encontrado el 01/09/2026 pidiendo el rol granular: antes la unica forma
 * de darle a alguien acceso al catalogo o a la caja era hacerlo ADMIN entero,
 * lo que tambien le daba anular ventas, ver reportes y gestionar usuarios.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class PermisosGranularesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private static final RequestPostProcessor CON_STOCK = user("empleado-stock")
            .authorities(new SimpleGrantedAuthority("ROLE_EMPLEADO"), new SimpleGrantedAuthority("PERM_STOCK"));

    private static final RequestPostProcessor CON_CAJA = user("empleado-caja")
            .authorities(new SimpleGrantedAuthority("ROLE_EMPLEADO"), new SimpleGrantedAuthority("PERM_CAJA"));

    private static final RequestPostProcessor SIN_PERMISOS = user("empleado-pelado")
            .authorities(new SimpleGrantedAuthority("ROLE_EMPLEADO"));

    private void permitido(MvcResult resultado, String queEs) {
        assertNotEquals(403, resultado.getResponse().getStatus(), queEs + " tendria que estar permitido");
    }

    private void prohibido(MvcResult resultado, String queEs) {
        assertEquals(403, resultado.getResponse().getStatus(), queEs + " tendria que estar prohibido");
    }

    @Test
    void unEmpleadoConPermStockPuedeTocarElCatalogo() throws Exception {
        permitido(mockMvc.perform(post("/productos").with(CON_STOCK)).andReturn(), "crear un producto con PERM_STOCK");
        permitido(mockMvc.perform(post("/categorias").with(CON_STOCK)).andReturn(), "crear una categoria con PERM_STOCK");
    }

    @Test
    void unEmpleadoConPermCajaPuedeVerElHistorial() throws Exception {
        permitido(mockMvc.perform(get("/caja/movimientos?desde=2026-01-01&hasta=2026-01-01").with(CON_CAJA))
                .andReturn(), "ver el historial completo de caja con PERM_CAJA");
    }

    @Test
    void sinPermCajaNoSeVeNadaDeLaCaja() throws Exception {
        // Reportado por Martin el 02/09/2026 mirando la aplicacion: "aunque el
        // empleado pepe no tenga permiso de caja, puede ver la caja". Era
        // cierto: GET /caja y /caja/ultimos estaban como authenticated(), con
        // el argumento de que el que atiende necesita saber con que efectivo
        // cuenta para dar vuelto. Pero el permiso se llama "puede ver caja":
        // si el dueño no se lo prendio, no ve la caja. El saldo del negocio no
        // es un dato de mostrador.
        prohibido(mockMvc.perform(get("/caja").with(SIN_PERMISOS)).andReturn(),
                "ver el saldo de caja sin permiso");
        prohibido(mockMvc.perform(get("/caja/ultimos").with(SIN_PERMISOS)).andReturn(),
                "ver los ultimos movimientos sin permiso");
    }

    @Test
    void conPermCajaSiSeVeElSaldo() throws Exception {
        // La otra mitad: prender el permiso tiene que alcanzar, sin volver
        // ADMIN a nadie.
        permitido(mockMvc.perform(get("/caja").with(CON_CAJA)).andReturn(),
                "ver el saldo con PERM_CAJA");
        permitido(mockMvc.perform(get("/caja/ultimos").with(CON_CAJA)).andReturn(),
                "ver los ultimos movimientos con PERM_CAJA");
    }

    @Test
    void permCajaNoAlcanzaParaCargarUnMovimiento() throws Exception {
        // Ver si, mover plata no: eso sigue siendo del dueño aunque tenga
        // PERM_CAJA (ver SecurityConfig, comentario de /caja/**).
        prohibido(mockMvc.perform(post("/caja/arqueo").with(CON_CAJA)).andReturn(),
                "cargar un arqueo solo con PERM_CAJA");
    }

    @Test
    void permStockNoAlcanzaParaVerCaja() throws Exception {
        // Los permisos son independientes entre si.
        prohibido(mockMvc.perform(get("/caja/movimientos?desde=2026-01-01&hasta=2026-01-01").with(CON_STOCK))
                .andReturn(), "ver caja solo con PERM_STOCK");
    }

    @Test
    void unEmpleadoSinPermisosSigueSinPoderTocarNada() throws Exception {
        prohibido(mockMvc.perform(post("/productos").with(SIN_PERMISOS)).andReturn(), "crear un producto sin permiso");
        prohibido(mockMvc.perform(get("/caja/movimientos?desde=2026-01-01&hasta=2026-01-01").with(SIN_PERMISOS))
                .andReturn(), "ver caja sin permiso");
    }

    @Test
    void actualizarPermisosLosDejaListosParaElProximoLogin() {
        Usuario empleado = usuarioRepository.save(Usuario.builder()
                .username("nuevo-empleado-" + System.nanoTime())
                .password("hash-cualquiera")
                .nombre("Empleado de prueba")
                .rol("EMPLEADO")
                .build());

        PermisosUsuarioDTO permisos = new PermisosUsuarioDTO();
        permisos.setPuedeGestionarProductos(true);
        permisos.setPuedeVerCaja(true);

        UsuarioDTO actualizado = usuarioService.actualizarPermisos(empleado.getId(), permisos);

        assertThat(actualizado.isPuedeGestionarProductos()).isTrue();
        assertThat(actualizado.isPuedeVerCaja()).isTrue();

        Usuario recargado = usuarioRepository.findById(empleado.getId()).orElseThrow();
        assertThat(recargado.isPuedeGestionarProductos()).isTrue();
        assertThat(recargado.isPuedeVerCaja()).isTrue();
    }
}
