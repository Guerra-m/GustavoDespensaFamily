package com.guerram.DespensaFamily.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * Garantiza que cada instalacion firme sus tokens con un secreto propio.
 *
 * <p>El valor que trae {@code application.properties} viaja adentro del jar y
 * esta en el repositorio, asi que seria identico en todas las copias: quien lo
 * conozca puede fabricarse un token de administrador valido. Con la aplicacion
 * accesible desde el wifi del local, eso deja de ser un detalle de desarrollo.
 *
 * <p>El secreto se genera una sola vez, en el primer arranque, y queda en
 * {@code config.properties} dentro de la carpeta de datos — fuera del jar, y
 * por lo tanto fuera de lo que pisa una actualizacion.
 */
public final class SecretoJwt {

    public static final String CLAVE = "jwt.secret";

    /** 32 bytes: el minimo que exige HS256 en JwtService. */
    private static final int BYTES = 32;

    private static final SecureRandom AZAR = new SecureRandom();

    private SecretoJwt() {
    }

    /**
     * Sin relleno ni caracteres especiales: el valor termina en un archivo de
     * propiedades, donde '\' es escape y ':' y '=' separan clave de valor.
     */
    public static String generar() {
        byte[] bytes = new byte[BYTES];
        AZAR.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Devuelve el secreto de esta instalacion, generandolo si todavia no hay.
     * Idempotente: si ya existe uno, lo respeta y no toca el archivo.
     */
    public static String asegurar(Path configProperties) throws IOException {
        String existente = leer(configProperties);
        if (existente != null && !existente.isBlank()) {
            return existente;
        }

        String nuevo = generar();
        escribir(configProperties, nuevo, existente != null);
        return nuevo;
    }

    private static String leer(Path configProperties) throws IOException {
        if (!Files.isRegularFile(configProperties)) {
            return null;
        }
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(configProperties)) {
            propiedades.load(entrada);
        }
        return propiedades.getProperty(CLAVE);
    }

    /**
     * Se edita el texto en vez de usar {@link Properties#store}: eso reescribe
     * el archivo entero y se lleva puestos los comentarios y el orden, que son
     * lo unico que hace entendible un config.properties editado a mano.
     */
    private static void escribir(Path configProperties, String secreto, boolean habiaClaveEnBlanco)
            throws IOException {

        Files.createDirectories(configProperties.toAbsolutePath().getParent());

        if (!Files.exists(configProperties)) {
            Files.writeString(configProperties, cabecera() + CLAVE + "=" + secreto + "\n",
                    StandardCharsets.UTF_8);
            return;
        }

        String contenido = Files.readString(configProperties, StandardCharsets.UTF_8);

        if (habiaClaveEnBlanco) {
            contenido = contenido.replaceAll("(?m)^\\s*" + CLAVE + "\\s*[=:].*$",
                    CLAVE + "=" + secreto);
            Files.writeString(configProperties, contenido, StandardCharsets.UTF_8);
            return;
        }

        String separador = contenido.isEmpty() || contenido.endsWith("\n") ? "" : "\n";
        Files.writeString(configProperties,
                separador + cabecera() + CLAVE + "=" + secreto + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static String cabecera() {
        return """
                # Secreto de firma de los tokens de sesion, generado en el primer
                # arranque y unico de esta instalacion. No compartirlo ni copiarlo
                # a otra PC: quien lo tenga puede fabricar un token de administrador.
                # Si se cambia, todos los usuarios tienen que volver a entrar.
                """;
    }
}
