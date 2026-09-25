package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DF-SEC-004: la contraseña inicial del admin salía por el log de arranque
 * ({@code log.warn(mensaje)} con la contraseña adentro), que es lo primero
 * que se pide cuando algo falla en el local — y con eso, cualquiera con
 * acceso al log (soporte remoto, un .log adjuntado por error) se queda con
 * la contraseña del dueño. Tiene que quedar solo en
 * {@code credenciales-iniciales.txt}, con el log apuntando adónde mirar.
 *
 * <p>No es un {@code @SpringBootTest}: instanciar {@link DataInitializer} a
 * mano con mocks evita depender de si el contexto de Spring ya arrancó (y ya
 * creó el admin) en un test anterior de la misma corrida — acá el conteo de
 * usuarios en 0 está garantizado por el mock, no por el orden de ejecución.
 */
@ExtendWith(OutputCaptureExtension.class)
class DataInitializerNoLoguaLaContrasenaTest {

    // Forma exacta de CredencialInicial.generar(): 4 grupos de 4, separados
    // por guiones, sin 0/O/1/l/I/5/S. Si esto aparece en el log, es la
    // contraseña.
    private static final Pattern PATRON_DE_CONTRASENA =
            Pattern.compile("[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}");

    @Test
    void noLoguaLaContrasenaAlCrearElAdministradorInicial(CapturedOutput output, @org.junit.jupiter.api.io.TempDir Path carpetaDeDatos)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(usuarioRepository.count()).thenReturn(0L);
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hash-de-prueba");
        when(usuarioRepository.save(org.mockito.ArgumentMatchers.any(Usuario.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DataInitializer dataInitializer = new DataInitializer(usuarioRepository, passwordEncoder);
        setCarpetaDeDatos(dataInitializer, carpetaDeDatos.toString());

        dataInitializer.run();

        String log = output.getOut() + output.getErr();
        assertFalse(PATRON_DE_CONTRASENA.matcher(log).find(),
                "el log de arranque no puede contener la contraseña inicial generada");

        // El aviso tiene que seguir existiendo: solo se saca la contraseña,
        // no la referencia a dónde encontrarla.
        assertTrue(log.contains("credenciales-iniciales.txt"),
                "el log tiene que decir dónde quedó anotada la contraseña");

        Path archivo = carpetaDeDatos.resolve("credenciales-iniciales.txt");
        assertTrue(Files.exists(archivo), "el archivo con la contraseña real tiene que existir");
        String contenidoArchivo = Files.readString(archivo);
        assertTrue(PATRON_DE_CONTRASENA.matcher(contenidoArchivo).find(),
                "la contraseña real tiene que seguir estando en el archivo, que es su único lugar ahora");
    }

    private static void setCarpetaDeDatos(DataInitializer instancia, String valor)
            throws NoSuchFieldException, IllegalAccessException {
        Field campo = DataInitializer.class.getDeclaredField("carpetaDeDatos");
        campo.setAccessible(true);
        campo.set(instancia, valor);
    }
}
