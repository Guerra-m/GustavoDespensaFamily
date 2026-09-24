package com.guerram.DespensaFamily.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Deja escrito, para la ventana de escritorio, por que el servidor se nego a
 * arrancar. El launcher no ve la consola del servidor: sin esto, lo unico que
 * puede decir es "se quedo esperando", que no ayuda a nadie a resolverlo.
 *
 * <p>Solo se anotan los motivos que el usuario puede resolver solo. El resto ya
 * tiene su lugar: el log, que el cartel de la ventana indica donde esta.
 */
public final class ErrorDeArranque {

    public static final String ARCHIVO = "error-de-arranque.txt";

    private ErrorDeArranque() {
    }

    /** Devuelve true si el error era de los que se explican al usuario. */
    public static boolean anotar(Throwable error, Path carpetaDeLogs) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof BaseDeUnaVersionMasNuevaException) {
                try {
                    Files.createDirectories(carpetaDeLogs);
                    Files.writeString(carpetaDeLogs.resolve(ARCHIVO), t.getMessage(), StandardCharsets.UTF_8);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
            if (t.getCause() == t) break;
        }
        return false;
    }
}
