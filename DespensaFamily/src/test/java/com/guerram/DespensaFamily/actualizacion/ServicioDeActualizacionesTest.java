package com.guerram.DespensaFamily.actualizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * El servidor no sale a internet: el launcher hace la consulta, la descarga y
 * la verificacion, y deja el resultado escrito en estado.json. Este servicio
 * solo lee ese archivo y decide que mostrar.
 *
 * <p>Esa division es a proposito. El servidor es el que atiende la caja y esta
 * expuesto a la red del local; no tiene por que aprender a bajar archivos de
 * internet ni a ejecutar nada. El launcher, que ya es duenio del ciclo de vida
 * y de los archivos del programa, es el lugar natural para eso.
 */
class ServicioDeActualizacionesTest {

    @TempDir
    Path carpeta;

    private ServicioDeActualizaciones servicio(String versionInstalada) {
        return new ServicioDeActualizaciones(carpeta, versionInstalada);
    }

    private void estadoJson(String contenido) throws IOException {
        Files.writeString(carpeta.resolve("estado.json"), contenido);
    }

    @Test
    void sinLauncherNoHayAvisoDeActualizacion() {
        // Correr el jar suelto (iniciar.bat, o desarrollo) es un caso valido: no
        // hay quien aplique la actualizacion, asi que no se ofrece.
        EstadoDeActualizacion estado = servicio("1.1.0").estado();

        assertThat(estado.gestionadoPorLauncher()).isFalse();
        assertThat(estado.hayActualizacion()).isFalse();
        assertThat(estado.versionInstalada()).isEqualTo("1.1.0");
    }

    @Test
    void siYaEstamosEnLaUltimaNoSeAvisaNada() throws IOException {
        estadoJson("{\"estado\":\"al-dia\",\"version\":\"1.1.0\"}");

        assertThat(servicio("1.1.0").estado().hayActualizacion()).isFalse();
    }

    @Test
    void avisaCuandoHayUnaVersionPosterior() throws IOException {
        estadoJson("{\"estado\":\"disponible\",\"version\":\"1.2.0\",\"notas\":\"Arregla los tickets\"}");

        EstadoDeActualizacion estado = servicio("1.1.0").estado();

        assertThat(estado.hayActualizacion()).isTrue();
        assertThat(estado.versionDisponible()).isEqualTo("1.2.0");
        assertThat(estado.notas()).isEqualTo("Arregla los tickets");
        // Todavia no se descargo: se avisa, pero el boton no puede aplicar nada.
        assertThat(estado.listaParaAplicar()).isFalse();
    }

    @Test
    void soloEstaListaCuandoElLauncherLaDescargoYVerifico() throws IOException {
        estadoJson("{\"estado\":\"lista\",\"version\":\"1.2.0\"}");

        assertThat(servicio("1.1.0").estado().listaParaAplicar()).isTrue();
    }

    @Test
    void despuesDeActualizarElAvisoDesaparece() throws IOException {
        // estado.json queda del ciclo anterior, con la version que ya se aplico.
        // Si el aviso saliera de ese archivo y no de la comparacion, el cartel
        // quedaria prendido para siempre.
        estadoJson("{\"estado\":\"lista\",\"version\":\"1.2.0\"}");

        assertThat(servicio("1.2.0").estado().hayActualizacion()).isFalse();
    }

    @Test
    void unEstadoJsonRotoNoRompeLaAplicacion() throws IOException {
        estadoJson("esto no es json");

        EstadoDeActualizacion estado = servicio("1.1.0").estado();

        // La caja tiene que seguir vendiendo aunque el archivo este corrupto.
        assertThat(estado.hayActualizacion()).isFalse();
        assertThat(estado.versionInstalada()).isEqualTo("1.1.0");
    }

    @Test
    void aplicarDejaLaSenialQueElLauncherVaALeer() throws IOException {
        estadoJson("{\"estado\":\"lista\",\"version\":\"1.2.0\"}");

        servicio("1.1.0").pedirQueSeAplique();

        Path senial = carpeta.resolve("aplicar");
        assertThat(Files.exists(senial)).isTrue();
        // Deja anotada la version pedida: si el launcher encuentra otra cosa en
        // la carpeta, sabe que no es lo que se aprobo.
        assertThat(Files.readString(senial)).contains("1.2.0");
    }

    @Test
    void noSePuedeAplicarLoQueNoEstaDescargado() throws IOException {
        estadoJson("{\"estado\":\"disponible\",\"version\":\"1.2.0\"}");

        assertThatThrownBy(() -> servicio("1.1.0").pedirQueSeAplique())
                .isInstanceOf(IllegalStateException.class);

        assertThat(Files.exists(carpeta.resolve("aplicar"))).isFalse();
    }

    @Test
    void noSePuedeAplicarSinLauncher() {
        assertThatThrownBy(() -> servicio("1.1.0").pedirQueSeAplique())
                .isInstanceOf(IllegalStateException.class);
    }
}
