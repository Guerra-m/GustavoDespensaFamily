package com.guerram.DespensaFamily.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lee y escribe propiedades sueltas dentro de {@code config.properties}
 * preservando todo lo demas que el archivo ya tenia (comentarios, orden,
 * otras claves como {@link SecretoJwt#CLAVE}), y con escritura atomica: si
 * se corta la luz a mitad de la escritura, el archivo real nunca queda a
 * medio escribir (SEC-02 — un config.properties corrupto se lleva puesto el
 * secreto de sesion de la instalacion, no solo la propiedad que se estaba
 * cambiando).
 *
 * <p>Nace como extraccion de la logica que ya tenia {@link SecretoJwt} para
 * no duplicarla con la clave de Gemini, que se guarda en el mismo archivo.
 */
public final class EditorArchivoPropiedades {

    private EditorArchivoPropiedades() {
    }

    /** Null si el archivo no existe o la propiedad no esta. */
    public static String leerValor(Path archivo, String clave) throws IOException {
        if (!Files.isRegularFile(archivo)) {
            return null;
        }
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(archivo)) {
            propiedades.load(entrada);
        }
        return propiedades.getProperty(clave);
    }

    /**
     * Agrega o reemplaza {@code clave=valor}, preservando el resto del
     * archivo. Se edita el texto en vez de usar {@link Properties#store}:
     * eso reescribe el archivo entero y se lleva puestos los comentarios y
     * el orden, que son lo unico que hace entendible un config.properties
     * editado a mano.
     *
     * @param cabecera comentario que se agrega SOLO si la propiedad es
     *                 nueva (si ya existia, se reemplaza en el lugar sin
     *                 repetir el comentario). Puede ser null.
     */
    public static void escribirValor(Path archivo, String clave, String valor, String cabecera)
            throws IOException {

        String contenidoActual = Files.isRegularFile(archivo)
                ? Files.readString(archivo, StandardCharsets.UTF_8)
                : "";

        String patronLinea = "(?m)^[ \\t]*" + java.util.regex.Pattern.quote(clave) + "[ \\t]*[=:].*$";
        String lineaNueva = clave + "=" + valor;

        String contenidoFinal;
        if (contenidoActual.matches("(?s).*" + patronLinea + ".*")) {
            contenidoFinal = contenidoActual.replaceAll(patronLinea, java.util.regex.Matcher.quoteReplacement(lineaNueva));
        } else {
            String textoCabecera = cabecera != null ? cabecera : "";
            String separador = contenidoActual.isEmpty() || contenidoActual.endsWith("\n") ? "" : "\n";
            contenidoFinal = contenidoActual + separador + textoCabecera + lineaNueva + "\n";
        }

        escribirAtomico(archivo, contenidoFinal);
    }

    /** Quita la propiedad si existe. No hace nada si el archivo o la clave no existen. */
    public static void quitarValor(Path archivo, String clave) throws IOException {
        quitarValor(archivo, clave, null);
    }

    /**
     * Como {@link #quitarValor(Path, String)}, pero si la linea tiene justo
     * arriba el comentario {@code cabecera} (el mismo que se paso a
     * {@link #escribirValor}), se va con ella: si no, cada ciclo de cargar y
     * quitar dejaria un comentario huerfano mas en el archivo.
     */
    public static void quitarValor(Path archivo, String clave, String cabecera) throws IOException {
        if (!Files.isRegularFile(archivo)) {
            return;
        }
        String contenidoActual = Files.readString(archivo, StandardCharsets.UTF_8);
        String patronCabecera = "";
        if (cabecera != null && !cabecera.isEmpty()) {
            StringBuilder lineas = new StringBuilder();
            for (String linea : cabecera.split("\\r?\\n")) {
                lineas.append(Pattern.quote(linea)).append("\\r?\\n");
            }
            patronCabecera = "(?:" + lineas + ")?";
        }
        String patronLinea = "(?m)^" + patronCabecera
                + "[ \\t]*" + Pattern.quote(clave) + "[ \\t]*[=:].*(\\r?\\n|$)";
        String contenidoFinal = contenidoActual.replaceAll(patronLinea, "");
        if (!contenidoFinal.equals(contenidoActual)) {
            escribirAtomico(archivo, contenidoFinal);
        }
    }

    /**
     * Escribe a un temporal en la misma carpeta y lo mueve con ATOMIC_MOVE:
     * un corte de luz a mitad de camino deja el temporal a medio escribir,
     * nunca el archivo real (que sigue siendo la version anterior completa
     * hasta que el move, atomico, lo reemplaza de un solo golpe).
     */
    private static void escribirAtomico(Path archivo, String contenido) throws IOException {
        Path carpeta = archivo.toAbsolutePath().getParent();
        Files.createDirectories(carpeta);
        Path temporal = carpeta.resolve(archivo.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.writeString(temporal, contenido, StandardCharsets.UTF_8);
            Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }
}
