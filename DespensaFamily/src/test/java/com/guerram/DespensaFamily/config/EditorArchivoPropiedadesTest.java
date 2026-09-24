package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Editor de config.properties compartido entre SecretoJwt y la clave de IA
 * (SEC-02/SEC-05): un solo lugar que sabe preservar el resto del archivo y
 * escribir de forma atomica, para no duplicar esa logica en cada feature que
 * necesite guardar algo ahi.
 */
class EditorArchivoPropiedadesTest {

    @Test
    void agregaLaPropiedadCuandoElArchivoNoExiste(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("config.properties");

        EditorArchivoPropiedades.escribirValor(archivo, "clave.x", "valor1", null);

        assertTrue(Files.exists(archivo));
        assertEquals("valor1", EditorArchivoPropiedades.leerValor(archivo, "clave.x"));
    }

    @Test
    void conservaElRestoDelArchivoAlAgregarUnaPropiedadNueva(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("config.properties");
        Files.writeString(archivo, "# comentario\njwt.secret=el-secreto-existente\nserver.port=9100\n");

        EditorArchivoPropiedades.escribirValor(archivo, "clave.x", "valor1", null);

        String contenido = Files.readString(archivo);
        assertTrue(contenido.contains("jwt.secret=el-secreto-existente"), "se perdio el secreto de jwt");
        assertTrue(contenido.contains("server.port=9100"), "se perdio otra propiedad");
        assertTrue(contenido.contains("# comentario"), "se perdio el comentario");
        assertEquals("valor1", EditorArchivoPropiedades.leerValor(archivo, "clave.x"));
    }

    @Test
    void reemplazaUnaPropiedadExistenteSinDuplicarla(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("config.properties");
        Files.writeString(archivo, "clave.x=viejo\njwt.secret=intacto\n");

        EditorArchivoPropiedades.escribirValor(archivo, "clave.x", "nuevo", null);

        String contenido = Files.readString(archivo);
        assertEquals("nuevo", EditorArchivoPropiedades.leerValor(archivo, "clave.x"));
        assertTrue(contenido.contains("jwt.secret=intacto"));
        assertEquals(1, contenido.split("clave\\.x=", -1).length - 1, "no tiene que quedar duplicada");
    }

    @Test
    void agregaElComentarioSoloCuandoEsUnaPropiedadNueva(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("config.properties");

        EditorArchivoPropiedades.escribirValor(archivo, "clave.x", "valor1", "# ayuda\n");
        String contenido = Files.readString(archivo);
        assertTrue(contenido.contains("# ayuda"));

        EditorArchivoPropiedades.escribirValor(archivo, "clave.x", "valor2", "# ayuda repetida\n");
        String contenidoLuego = Files.readString(archivo);
        assertEquals(1, contenidoLuego.split("# ayuda", -1).length - 1,
                "no hay que repetir el comentario al reemplazar");
    }

    @Test
    void quitaUnaPropiedadExistente(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("config.properties");
        Files.writeString(archivo, "clave.x=valor1\njwt.secret=intacto\n");

        EditorArchivoPropiedades.quitarValor(archivo, "clave.x");

        String contenido = Files.readString(archivo);
        assertFalse(contenido.contains("clave.x"));
        assertTrue(contenido.contains("jwt.secret=intacto"));
        assertNull(EditorArchivoPropiedades.leerValor(archivo, "clave.x"));
    }

    @Test
    void quitarUnaPropiedadQueNoExisteNoHaceNada(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("config.properties");
        Files.writeString(archivo, "jwt.secret=intacto\n");

        EditorArchivoPropiedades.quitarValor(archivo, "clave.x");

        assertEquals("jwt.secret=intacto\n", Files.readString(archivo));
    }

    @Test
    void quitarSobreUnArchivoInexistenteNoFalla(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("no-existe.properties");
        EditorArchivoPropiedades.quitarValor(archivo, "clave.x");
        assertFalse(Files.exists(archivo));
    }

    @Test
    void leerValorDevuelveNullSiElArchivoNoExiste(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("no-existe.properties");
        assertNull(EditorArchivoPropiedades.leerValor(archivo, "clave.x"));
    }

    @Test
    void escribirNoDejaArchivosTemporalesSueltos(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("config.properties");

        EditorArchivoPropiedades.escribirValor(archivo, "clave.x", "valor1", null);
        EditorArchivoPropiedades.escribirValor(archivo, "clave.x", "valor2", null);
        EditorArchivoPropiedades.quitarValor(archivo, "clave.x");

        try (var listado = Files.list(carpeta)) {
            long archivosSueltos = listado.filter(p -> !p.equals(archivo)).count();
            assertEquals(0, archivosSueltos, "quedo un temporal sin limpiar");
        }
    }

    @Test
    void quitarConCabeceraLaBorraAunqueElArchivoUseCrlf(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("config.properties");
        Files.writeString(archivo, "jwt.secret=intacto\r\n# Cabecera\r\n# de dos lineas\r\nclave.x=valor\r\n");

        EditorArchivoPropiedades.quitarValor(archivo, "clave.x", "# Cabecera\n# de dos lineas\n");

        assertEquals("jwt.secret=intacto\r\n", Files.readString(archivo));
    }

    @Test
    void quitarConCabeceraNoTocaUnComentarioQueNoEstaJustoArriba(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("config.properties");
        Files.writeString(archivo, "# Cabecera\njwt.secret=intacto\nclave.x=valor\n");

        EditorArchivoPropiedades.quitarValor(archivo, "clave.x", "# Cabecera\n");

        assertEquals("# Cabecera\njwt.secret=intacto\n", Files.readString(archivo));
    }
}
