package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Secreto de firma de los tokens, uno distinto por instalacion (F5).
 *
 * <p>El valor que trae application.properties es el mismo en todas las copias y
 * ademas esta en el repositorio: con el se puede firmar un token de admin. Con
 * la aplicacion accesible desde el wifi del local, eso deja de ser aceptable.
 */
class SecretoJwtTest {

    @Test
    void generaUnSecretoDe256Bits() {
        String secreto = SecretoJwt.generar();

        // JwtService hace Keys.hmacShaKeyFor(secret.getBytes()): HS256 exige
        // al menos 32 bytes o tira excepcion al firmar.
        assertTrue(secreto.getBytes(StandardCharsets.UTF_8).length >= 32,
                "el secreto tiene que dar al menos 32 bytes, dio " + secreto.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void dosSecretosNuncaSonIguales() {
        assertNotEquals(SecretoJwt.generar(), SecretoJwt.generar());
    }

    @Test
    void noUsaCaracteresQueRompanUnArchivoDePropiedades() {
        for (int i = 0; i < 50; i++) {
            String secreto = SecretoJwt.generar();
            assertTrue(secreto.matches("[A-Za-z0-9_-]+"), "secreto con caracteres raros: " + secreto);
        }
    }

    @Test
    void creaElArchivoSiNoExiste(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");

        String secreto = SecretoJwt.asegurar(config);

        assertTrue(Files.exists(config));
        assertTrue(Files.readString(config).contains("jwt.secret=" + secreto));
    }

    @Test
    void conservaElContenidoQueYaTeniaElArchivo(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "# configuracion de la instalacion\nserver.port=9100\n");

        SecretoJwt.asegurar(config);

        String contenido = Files.readString(config);
        assertTrue(contenido.contains("server.port=9100"), "se perdio el puerto configurado");
        assertTrue(contenido.contains("# configuracion de la instalacion"), "se perdieron los comentarios");
        assertTrue(contenido.contains("jwt.secret="), "no se agrego el secreto");
    }

    @Test
    void noCambiaUnSecretoQueYaExiste(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "jwt.secret=el-secreto-que-ya-estaba-y-es-bien-largo\n");

        String secreto = SecretoJwt.asegurar(config);

        assertEquals("el-secreto-que-ya-estaba-y-es-bien-largo", secreto);
        assertEquals(1, Files.readString(config).split("jwt.secret=", -1).length - 1,
                "no tiene que quedar el secreto duplicado");
    }

    @Test
    void reemplazaUnSecretoEnBlanco(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "jwt.secret=\n");

        String secreto = SecretoJwt.asegurar(config);

        assertTrue(secreto.length() > 30);
        assertTrue(Files.readString(config).contains("jwt.secret=" + secreto));
    }

    @Test
    void llamarloDosVecesDevuelveElMismoSecreto(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");

        assertEquals(SecretoJwt.asegurar(config), SecretoJwt.asegurar(config));
    }
}
