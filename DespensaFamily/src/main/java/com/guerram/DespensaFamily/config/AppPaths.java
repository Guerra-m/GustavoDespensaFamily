package com.guerram.DespensaFamily.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resuelve la carpeta de datos de la aplicación y la publica como propiedades
 * del sistema, para que {@code application.properties} las use con ${...}.
 *
 * <p>Separar el programa de los datos es lo que permite que actualizar sea
 * reinstalar: el instalador pisa la carpeta del programa y nunca toca ésta.
 * Antes, la base vivía en un MySQL externo y las imágenes en C:/uploads
 * hardcodeado — ninguna de las dos sobrevivía a una instalación limpia.
 *
 * <pre>
 * %LOCALAPPDATA%\DespensaFamily\
 * ├── db\         base H2 en archivo
 * ├── imagenes\   fotos de productos subidas por el usuario
 * ├── backups\    copias automáticas
 * └── logs\
 * </pre>
 */
public final class AppPaths {

    public static final String PROP_HOME = "despensa.home";
    public static final String PROP_DB = "despensa.db";
    public static final String PROP_IMAGENES = "despensa.imagenes";
    public static final String PROP_BACKUPS = "despensa.backups";
    public static final String PROP_LOGS = "despensa.logs";

    /** Permite mover la carpeta de datos sin recompilar (útil para probar). */
    private static final String ENV_HOME = "DESPENSA_HOME";

    private static final String CARPETA_WINDOWS = "DespensaFamily";
    private static final String CARPETA_UNIX = ".despensafamily";

    private AppPaths() {
    }

    /**
     * Crea la carpeta de datos si no existe y publica las rutas como
     * propiedades del sistema. Idempotente: se puede llamar más de una vez.
     */
    public static Path bootstrap() {
        Path home = resolveHome(
                primerValor(System.getProperty(PROP_HOME), System.getenv(ENV_HOME)),
                System.getenv("LOCALAPPDATA"),
                System.getProperty("user.home"));

        publicar(PROP_HOME, crear(home));
        publicar(PROP_DB, crear(db(home)));
        publicar(PROP_IMAGENES, crear(imagenes(home)));
        publicar(PROP_BACKUPS, crear(backups(home)));
        publicar(PROP_LOGS, crear(logs(home)));

        return home;
    }

    static Path resolveHome(String override, String localAppData, String userHome) {
        if (tieneValor(override)) {
            return Paths.get(override.trim());
        }
        if (tieneValor(localAppData)) {
            return Paths.get(localAppData.trim()).resolve(CARPETA_WINDOWS);
        }
        return Paths.get(userHome).resolve(CARPETA_UNIX);
    }

    static Path db(Path home) {
        return home.resolve("db");
    }

    static Path imagenes(Path home) {
        return home.resolve("imagenes");
    }

    static Path backups(Path home) {
        return home.resolve("backups");
    }

    static Path logs(Path home) {
        return home.resolve("logs");
    }

    private static Path crear(Path carpeta) {
        try {
            Files.createDirectories(carpeta);
            return carpeta;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta de datos: " + carpeta, e);
        }
    }

    /**
     * Se publica con barras normales: la ruta termina dentro de una URL JDBC
     * y de un {@code file:} de Spring, donde las barras invertidas de Windows
     * se leen como escapes.
     */
    private static void publicar(String propiedad, Path ruta) {
        System.setProperty(propiedad, ruta.toString().replace('\\', '/'));
    }

    private static String primerValor(String... valores) {
        for (String valor : valores) {
            if (tieneValor(valor)) {
                return valor;
            }
        }
        return null;
    }

    private static boolean tieneValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
