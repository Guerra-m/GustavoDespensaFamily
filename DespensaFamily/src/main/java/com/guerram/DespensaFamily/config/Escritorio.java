package com.guerram.DespensaFamily.config;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Decisiones del launcher de escritorio que no dependen de abrir una ventana:
 * que URL abrir, en que puerto esta la app, como se llama un respaldo y si ya
 * hay otra copia corriendo.
 */
public final class Escritorio {

    public static final int PUERTO_POR_DEFECTO = 8080;

    private static final String PAGINA_LOGIN = "/src/pages/login/login.html";
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final String ARG_PUERTO = "--server.port=";

    private Escritorio() {
    }

    public static String urlLogin(int puerto) {
        return "http://localhost:" + puerto + PAGINA_LOGIN;
    }

    /**
     * El puerto sale de config.properties, que vive en la carpeta de datos y no
     * dentro del jar. Cualquier problema al leerlo cae al valor por defecto: el
     * launcher nunca deberia impedir que la app abra.
     */
    public static int puertoConfigurado(Path configProperties, int porDefecto) {
        if (!Files.isRegularFile(configProperties)) {
            return porDefecto;
        }
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(configProperties)) {
            propiedades.load(entrada);
        } catch (IOException e) {
            return porDefecto;
        }
        String valor = propiedades.getProperty("server.port");
        if (valor == null) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    /** Zip porque lo genera "BACKUP TO" de H2, que escribe una copia consistente. */
    public static String nombreDeRespaldo(LocalDateTime momento) {
        return "despensa-" + SELLO.format(momento) + ".zip";
    }

    /** El puerto puede venir por linea de comandos y tiene que ganar. */
    public static int puertoDeLosArgumentos(String[] argumentos, int porDefecto) {
        for (String argumento : argumentos) {
            if (argumento.startsWith(ARG_PUERTO)) {
                try {
                    return Integer.parseInt(argumento.substring(ARG_PUERTO.length()).trim());
                } catch (NumberFormatException e) {
                    return porDefecto;
                }
            }
        }
        return porDefecto;
    }

    /**
     * Pregunta por la pagina de login en vez de mirar solo si el puerto esta
     * ocupado: asi no confundimos "la app ya esta abierta" con "otro programa
     * agarro el 8080".
     */
    public static boolean yaEstaCorriendo(int puerto) {
        try {
            HttpURLConnection conexion = (HttpURLConnection) new URL(urlLogin(puerto)).openConnection();
            conexion.setRequestMethod("HEAD");
            conexion.setConnectTimeout(700);
            conexion.setReadTimeout(700);
            int codigo = conexion.getResponseCode();
            conexion.disconnect();
            return codigo == 200;
        } catch (IOException e) {
            return false;
        }
    }

    /** Silencioso a proposito: no poder abrir el navegador no es motivo para no arrancar. */
    public static void abrirNavegador(int puerto) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(urlLogin(puerto)));
            }
        } catch (Exception e) {
            System.out.println("No se pudo abrir el navegador solo. Entra a " + urlLogin(puerto));
        }
    }
}
