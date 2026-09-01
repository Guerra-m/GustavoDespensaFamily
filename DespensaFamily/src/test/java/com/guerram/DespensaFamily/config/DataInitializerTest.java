package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Siembra de usuarios al arrancar.
 *
 * <p>Hasta la auditoria del 01/09/2026 esto creaba tres empleados de ejemplo
 * —juan, maria y carlos, todos con "empleado123"— y los volvia a crear en cada
 * arranque aunque se los borrara. Sumado a que el servidor escucha en toda la
 * red del local, cualquiera con un telefono en el wifi entraba al sistema. Eso
 * se saco de raiz; estos tests son los que impiden que vuelva.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class DataInitializerTest {

    private static final String PASSWORD_DEMO_VIEJA = "empleado123";

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void noCreaLosEmpleadosDeEjemplo() {
        for (String demo : new String[]{"juan", "maria", "carlos"}) {
            assertTrue(usuarioRepository.findByUsername(demo).isEmpty(),
                    "el arranque creo la cuenta de ejemplo '" + demo + "'");
        }
    }

    @Test
    void elAdminInicialNoUsaLaContrasenaVieja() {
        Usuario admin = usuarioRepository.findByUsername("admin").orElseThrow();
        assertFalse(passwordEncoder.matches("admin123", admin.getPassword()),
                "el admin sigue teniendo la contrasena publica admin123");
    }

    @Test
    void elAdminInicialTieneQueCambiarLaContrasena() {
        Usuario admin = usuarioRepository.findByUsername("admin").orElseThrow();
        assertTrue(admin.isDebeCambiarPassword(),
                "el admin recien creado tendria que estar obligado a cambiar la contrasena");
    }

    @Test
    void borraLasCuentasDeEjemploQueQuedaronDeUnaInstalacionVieja() {
        // Simula la PC que ya tiene la version anterior instalada: al
        // actualizar, esas cuentas siguen ahi con la contrasena publica.
        usuarioRepository.save(Usuario.builder()
                .username("juan")
                .password(passwordEncoder.encode(PASSWORD_DEMO_VIEJA))
                .nombre("Juan García")
                .rol("EMPLEADO")
                .build());

        dataInitializer.run();

        assertTrue(usuarioRepository.findByUsername("juan").isEmpty(),
                "la cuenta de ejemplo con la contrasena publica tendria que haberse eliminado");
    }

    @Test
    void respetaUnaCuentaConEseNombreSiLeCambiaronLaContrasena() {
        // Caso real: al dueno le gusto el nombre y se lo dio a un empleado de
        // verdad con otra contrasena. Esa cuenta es legitima y no se toca.
        usuarioRepository.save(Usuario.builder()
                .username("maria")
                .password(passwordEncoder.encode("una-contrasena-propia-del-negocio"))
                .nombre("María López")
                .rol("EMPLEADO")
                .build());

        dataInitializer.run();

        assertTrue(usuarioRepository.findByUsername("maria").isPresent(),
                "se borro una cuenta legitima que solo compartia el nombre con una de ejemplo");
        usuarioRepository.deleteAll(usuarioRepository.findByUsername("maria").stream().toList());
    }

    @Test
    void noLeCambiaLaContrasenaAlAdminEnCadaArranque() {
        String antes = usuarioRepository.findByUsername("admin").orElseThrow().getPassword();

        dataInitializer.run();

        String despues = usuarioRepository.findByUsername("admin").orElseThrow().getPassword();
        assertEquals(antes, despues,
                "un reinicio no puede regenerar la contrasena del admin: dejaria afuera al dueno");
    }
}
