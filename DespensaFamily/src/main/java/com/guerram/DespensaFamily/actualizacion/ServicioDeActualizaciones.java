package com.guerram.DespensaFamily.actualizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lee lo que el launcher dejo escrito sobre actualizaciones y prepara la
 * respuesta para la pantalla.
 *
 * <p><b>Este servicio no sale a internet.</b> El servidor es el proceso que
 * atiende la caja y esta expuesto a la red del local: no tiene por que aprender
 * a descargar archivos ni a verificar firmas. Todo eso lo hace el launcher, que
 * ya es duenio de los archivos del programa y de su ciclo de vida, y deja el
 * resultado en dos archivos dentro de la carpeta de datos:
 *
 * <pre>
 * %LOCALAPPDATA%\DespensaFamily\actualizaciones\
 * ├── estado.json   lo escribe el launcher: que version encontro y si ya la bajo
 * ├── aplicar       lo escribe este servicio cuando el duenio toca "actualizar"
 * └── pendiente\    el jar nuevo, ya verificado, esperando el reinicio
 * </pre>
 *
 * <p>El aviso sale de comparar versiones y no de lo que diga estado.json,
 * porque ese archivo sobrevive al reinicio: si nos guiaramos por el, el cartel
 * de "hay una actualizacion" quedaria prendido despues de haber actualizado.
 */
public class ServicioDeActualizaciones {

    private static final Logger log = LoggerFactory.getLogger(ServicioDeActualizaciones.class);

    private static final String ARCHIVO_ESTADO = "estado.json";
    private static final String ARCHIVO_SENIAL = "aplicar";

    /** Lo escribe el launcher recien despues de verificar el SHA-256. */
    private static final String ESTADO_LISTA = "lista";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path carpeta;
    private final String versionInstalada;

    public ServicioDeActualizaciones(Path carpeta, String versionInstalada) {
        this.carpeta = carpeta;
        this.versionInstalada = versionInstalada;
    }

    public EstadoDeActualizacion estado() {
        JsonNode informe = leerInforme();
        if (informe == null) {
            return EstadoDeActualizacion.sinLauncher(versionInstalada);
        }

        String disponible = texto(informe, "version");
        boolean hay = Version.de(disponible).esPosteriorA(Version.de(versionInstalada));

        return new EstadoDeActualizacion(
                versionInstalada,
                disponible,
                hay,
                hay && ESTADO_LISTA.equals(texto(informe, "estado")),
                texto(informe, "notas"),
                true);
    }

    /**
     * Deja la senial que el launcher lee cuando el servidor termina. No aplica
     * nada por si misma: quien reemplaza el jar es el launcher, despues de que
     * este proceso haya cerrado la base con orden.
     *
     * @throws IllegalStateException si no hay una actualizacion descargada y
     *                               verificada esperando. Es una guarda real y
     *                               no defensiva: el boton de la pantalla puede
     *                               llegar tarde, con el estado ya cambiado.
     */
    public void pedirQueSeAplique() {
        EstadoDeActualizacion estado = estado();
        if (!estado.listaParaAplicar()) {
            throw new IllegalStateException("No hay ninguna actualizacion descargada para aplicar");
        }
        try {
            Files.writeString(carpeta.resolve(ARCHIVO_SENIAL), estado.versionDisponible());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo dejar la senial de actualizacion", e);
        }
    }

    private JsonNode leerInforme() {
        Path archivo = carpeta.resolve(ARCHIVO_ESTADO);
        if (!Files.isRegularFile(archivo)) {
            return null;
        }
        try {
            return JSON.readTree(Files.readString(archivo));
        } catch (Exception e) {
            // Un archivo corrupto no puede voltear la caja: se anota y se sigue
            // como si no hubiera launcher.
            log.warn("No se pudo leer {}: se ignora el aviso de actualizacion ({})",
                    archivo, e.getMessage());
            return null;
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }
}
