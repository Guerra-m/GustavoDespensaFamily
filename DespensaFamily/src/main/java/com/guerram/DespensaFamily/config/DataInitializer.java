package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Usuarios que existen apenas se termina de instalar.
 *
 * <p>Antes esto creaba, ademas del admin, tres empleados de ejemplo (juan,
 * maria, carlos) con la contrasena "empleado123" escrita en el codigo — y los
 * volvia a crear en cada arranque, asi que borrarlos no servia de nada. Como el
 * servidor escucha en toda la red del local, eso equivalia a dejar la puerta
 * abierta a cualquiera con un telefono conectado al wifi.
 *
 * <p>Ahora se crea un unico administrador con una contrasena generada al azar,
 * distinta en cada instalacion, que queda anotada en un archivo de la carpeta
 * de datos y que el sistema obliga a cambiar en el primer ingreso.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String ARCHIVO_CREDENCIAL = "credenciales-iniciales.txt";

    /** Las cuentas de ejemplo que traian las versiones anteriores. */
    private static final List<String> CUENTAS_DE_EJEMPLO = List.of("juan", "maria", "carlos");

    private static final String PASSWORD_EJEMPLO = "empleado123";
    private static final String PASSWORD_ADMIN_VIEJA = "admin123";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${despensa.home:}")
    private String carpetaDeDatos;

    @Override
    public void run(String... args) {
        limpiarCuentasDeEjemplo();
        migrarUsuariosSinRol();
        forzarCambioDeLaContrasenaVieja();

        if (usuarioRepository.count() == 0) {
            crearAdministradorInicial();
        }
    }

    /**
     * Saca las cuentas de ejemplo que quedaron de una instalacion anterior,
     * pero solo si todavia tienen la contrasena publica.
     *
     * <p>La comparacion contra la contrasena es lo que hace segura esta
     * limpieza: si al dueno le sirvio el nombre "juan" y se lo dio a un
     * empleado real con otra clave, esa cuenta es legitima y no se toca.
     * Borrar por nombre a secas se llevaria puesto un usuario del negocio.
     */
    private void limpiarCuentasDeEjemplo() {
        for (String nombreDeEjemplo : CUENTAS_DE_EJEMPLO) {
            usuarioRepository.findByUsername(nombreDeEjemplo)
                    .filter(u -> passwordEncoder.matches(PASSWORD_EJEMPLO, u.getPassword()))
                    .ifPresent(u -> {
                        log.warn("Se elimina la cuenta de ejemplo '{}', que todavia tenia la contrasena publica "
                                + "que traian las versiones anteriores", nombreDeEjemplo);
                        usuarioRepository.delete(u);
                    });
        }
    }

    /** Usuarios de versiones viejas, anteriores a que existieran los roles. */
    private void migrarUsuariosSinRol() {
        usuarioRepository.findAll().forEach(u -> {
            if (u.getRol() == null) {
                u.setRol("ADMIN");
                usuarioRepository.save(u);
            }
        });
    }

    /**
     * Una instalacion vieja puede seguir con el admin en "admin123". No se le
     * puede cambiar la contrasena por atras —dejaria al dueno afuera de su
     * propio sistema— pero si obligarlo a elegir una nueva al entrar.
     */
    private void forzarCambioDeLaContrasenaVieja() {
        usuarioRepository.findByUsername("admin")
                .filter(u -> !u.isDebeCambiarPassword())
                .filter(u -> passwordEncoder.matches(PASSWORD_ADMIN_VIEJA, u.getPassword()))
                .ifPresent(u -> {
                    log.warn("El administrador todavia usa la contrasena por defecto: se le va a exigir "
                            + "cambiarla en el proximo ingreso");
                    u.setDebeCambiarPassword(true);
                    usuarioRepository.save(u);
                });
    }

    private void crearAdministradorInicial() {
        String password = CredencialInicial.generar();

        usuarioRepository.save(Usuario.builder()
                .username("admin")
                .password(passwordEncoder.encode(password))
                .nombre("Administrador")
                .rol("ADMIN")
                .debeCambiarPassword(true)
                .build());

        anunciar(password);
    }

    /**
     * La contrasena se genera una sola vez y nadie la vio: si no queda escrita
     * en algun lado, la instalacion es inutilizable. Va a un archivo de la
     * carpeta de datos y ademas al log, que es lo primero que se pide cuando
     * algo falla en el local.
     */
    private void anunciar(String password) {
        String mensaje = """

                ===============================================================
                  DespensaFamily — primera vez que arranca en esta computadora

                  Usuario:    admin
                  Contrasena: %s

                  El sistema va a pedir cambiarla apenas se entre por primera
                  vez. Despues de eso, este archivo se puede borrar.
                ===============================================================
                """.formatted(password);

        log.warn(mensaje);
        guardarEnArchivo(mensaje);
    }

    private void guardarEnArchivo(String mensaje) {
        if (carpetaDeDatos == null || carpetaDeDatos.isBlank()) {
            return; // corriendo en tests o sin carpeta de datos: alcanza con el log
        }
        try {
            Path destino = Path.of(carpetaDeDatos).resolve(ARCHIVO_CREDENCIAL);
            Files.writeString(destino, mensaje, StandardCharsets.UTF_8);
            log.warn("La contrasena inicial tambien quedo anotada en {}", destino);
        } catch (IOException e) {
            // Que no se pueda escribir el archivo no puede impedir el arranque:
            // la contrasena ya salio por el log, que es la copia que importa.
            log.warn("No se pudo escribir el archivo con la contrasena inicial", e);
        }
    }
}
