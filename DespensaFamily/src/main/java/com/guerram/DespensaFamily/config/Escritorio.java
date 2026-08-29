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
    private static final String CLAVE_PUERTO = "server.port";
    private static final String CLAVE_MODO = "despensa.escritorio.modo";
    private static final String ARG_PUERTO = "--" + CLAVE_PUERTO + "=";
    private static final String ARG_MODO = "--" + CLAVE_MODO + "=";

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
        String valor = propiedadConfigurada(configProperties, CLAVE_PUERTO);
        if (valor == null) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    /**
     * Mismo criterio que el puerto: si el archivo no esta o el valor no se
     * entiende, gana el modo de siempre (abrir el navegador).
     */
    public static ModoEscritorio modoConfigurado(Path configProperties, ModoEscritorio porDefecto) {
        String valor = propiedadConfigurada(configProperties, CLAVE_MODO);
        return valor == null ? porDefecto : ModoEscritorio.desde(valor);
    }

    private static String propiedadConfigurada(Path configProperties, String clave) {
        if (!Files.isRegularFile(configProperties)) {
            return null;
        }
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(configProperties)) {
            propiedades.load(entrada);
        } catch (IOException e) {
            return null;
        }
        return propiedades.getProperty(clave);
    }

    /** Zip porque lo genera "BACKUP TO" de H2, que escribe una copia consistente. */
    public static String nombreDeRespaldo(LocalDateTime momento) {
        return "despensa-" + SELLO.format(momento) + ".zip";
    }

    /** El puerto puede venir por linea de comandos y tiene que ganar. */
    public static int puertoDeLosArgumentos(String[] argumentos, int porDefecto) {
        String valor = argumento(argumentos, ARG_PUERTO);
        if (valor == null) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    /**
     * El modo tambien puede venir por linea de comandos: es como el programa de
     * escritorio le avisa al servidor que la ventana la pone el, y por eso
     * tiene que ganarle a lo que diga config.properties.
     */
    public static ModoEscritorio modoDeLosArgumentos(String[] argumentos, ModoEscritorio porDefecto) {
        String valor = argumento(argumentos, ARG_MODO);
        return valor == null ? porDefecto : ModoEscritorio.desde(valor);
    }

    private static String argumento(String[] argumentos, String prefijo) {
        for (String argumento : argumentos) {
            if (argumento.startsWith(prefijo)) {
                return argumento.substring(prefijo.length()).trim();
            }
        }
        return null;
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
