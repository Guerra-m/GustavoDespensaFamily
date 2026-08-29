package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Logica del launcher de escritorio (F2). Todo lo que se puede decidir sin
 * abrir una ventana ni tocar la red vive aca, para poder probarlo.
 */
class EscritorioTest {

    @Test
    void armaLaUrlDelLoginConElPuerto() {
        assertEquals("http://localhost:8080/src/pages/login/login.html", Escritorio.urlLogin(8080));
        assertEquals("http://localhost:8099/src/pages/login/login.html", Escritorio.urlLogin(8099));
    }

    @Test
    void sinArchivoDeConfiguracionUsaElPuertoPorDefecto(@TempDir Path carpeta) {
        assertEquals(8080, Escritorio.puertoConfigurado(carpeta.resolve("config.properties"), 8080));
    }

    @Test
    void leeElPuertoDelArchivoDeConfiguracion(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "server.port=9100\n");

        assertEquals(9100, Escritorio.puertoConfigurado(config, 8080));
    }

    @Test
    void siElArchivoNoTraeElPuertoUsaElPorDefecto(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "jwt.secret=algo\n");

        assertEquals(8080, Escritorio.puertoConfigurado(config, 8080));
    }

    @Test
    void unPuertoInvalidoNoRompeElArranque(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "server.port=ocho mil\n");

        assertEquals(8080, Escritorio.puertoConfigurado(config, 8080));
    }

    // El respaldo lo genera "BACKUP TO" de H2, que escribe un zip consistente.
    // Copiar el .mv.db a mano con la base abierta puede dar un archivo a medias.
    @Test
    void elRespaldoSeNombraConLaFechaYLaHora() {
        LocalDateTime momento = LocalDateTime.of(2026, 8, 28, 23, 49);

        assertEquals("despensa-2026-08-28_23-49.zip", Escritorio.nombreDeRespaldo(momento));
    }

    @Test
    void dosRespaldosDeDistintaHoraNoSePisan() {
        String uno = Escritorio.nombreDeRespaldo(LocalDateTime.of(2026, 8, 28, 9, 5));
        String otro = Escritorio.nombreDeRespaldo(LocalDateTime.of(2026, 8, 28, 18, 30));

        assertEquals("despensa-2026-08-28_09-05.zip", uno);
        assertEquals("despensa-2026-08-28_18-30.zip", otro);
    }

    // El chequeo de instancia unica tiene que mirar el mismo puerto en el que
    // la app va a levantar, incluso cuando se lo pasan por linea de comandos.
    @Test
    void elArgumentoDeLineaDeComandosGanaSobreElPorDefecto() {
        assertEquals(8099, Escritorio.puertoDeLosArgumentos(new String[]{"--server.port=8099"}, 8080));
    }

    @Test
    void ignoraOtrosArgumentos() {
        assertEquals(8080, Escritorio.puertoDeLosArgumentos(new String[]{"--debug", "--spring.profiles.active=x"}, 8080));
    }

    @Test
    void sinArgumentosDevuelveElPorDefecto() {
        assertEquals(8080, Escritorio.puertoDeLosArgumentos(new String[]{}, 8080));
    }

    @Test
    void unArgumentoDePuertoInvalidoNoRompeElArranque() {
        assertEquals(8080, Escritorio.puertoDeLosArgumentos(new String[]{"--server.port=ocho"}, 8080));
    }

    // ---- Modo de escritorio (quien pone la ventana) ----

    @Test
    void sinArchivoDeConfiguracionElModoEsElPorDefecto(@TempDir Path carpeta) {
        assertEquals(ModoEscritorio.NAVEGADOR,
                Escritorio.modoConfigurado(carpeta.resolve("config.properties"), ModoEscritorio.NAVEGADOR));
    }

    @Test
    void leeElModoDelArchivoDeConfiguracion(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "despensa.escritorio.modo=ventana\n");

        assertEquals(ModoEscritorio.VENTANA, Escritorio.modoConfigurado(config, ModoEscritorio.NAVEGADOR));
    }

    @Test
    void sinModoEnLaLineaDeComandosGanaElPorDefecto() {
        assertEquals(ModoEscritorio.NAVEGADOR,
                Escritorio.modoDeLosArgumentos(new String[]{"--server.port=9000"}, ModoEscritorio.NAVEGADOR));
    }

    // El programa de escritorio arranca el servidor con este argumento: es como
    // le avisa que la ventana la pone el, asi que tiene que ganarle al archivo.
    @Test
    void elModoDeLaLineaDeComandosGanaSobreElPorDefecto() {
        assertEquals(ModoEscritorio.VENTANA,
                Escritorio.modoDeLosArgumentos(
                        new String[]{"--despensa.escritorio.modo=ventana"}, ModoEscritorio.NAVEGADOR));
    }
}
