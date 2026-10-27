package com.guerram.DespensaFamily.actualizacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Comparar versiones parece trivial hasta que "1.10.0" sale antes que "1.9.0"
 * porque alguien las comparo como texto. De eso depende que el aviso de
 * actualizacion aparezca cuando corresponde y, sobre todo, que NO aparezca
 * despues de actualizar.
 */
class VersionTest {

    @Test
    void unaVersionMayorEsMayor() {
        assertThat(Version.de("1.2.0").esPosteriorA(Version.de("1.1.0"))).isTrue();
        assertThat(Version.de("2.0.0").esPosteriorA(Version.de("1.9.9"))).isTrue();
    }

    @Test
    void compararPorNumeroYNoPorTexto() {
        // Como texto, "1.10.0" < "1.9.0". Es el error clasico y el motivo por el
        // que esta clase existe en vez de un simple compareTo de String.
        assertThat(Version.de("1.10.0").esPosteriorA(Version.de("1.9.0"))).isTrue();
        assertThat(Version.de("1.9.0").esPosteriorA(Version.de("1.10.0"))).isFalse();
    }

    @Test
    void laMismaVersionNoEsPosterior() {
        assertThat(Version.de("1.1.0").esPosteriorA(Version.de("1.1.0"))).isFalse();
    }

    @Test
    void unaVersionAnteriorNoEsPosterior() {
        assertThat(Version.de("1.0.0").esPosteriorA(Version.de("1.1.0"))).isFalse();
    }

    @Test
    void toleraCantidadDistintaDePartes() {
        // "1.2" y "1.2.0" son la misma version. Si esto fallara, el aviso de
        // actualizacion quedaria prendido para siempre despues de actualizar.
        assertThat(Version.de("1.2").esPosteriorA(Version.de("1.2.0"))).isFalse();
        assertThat(Version.de("1.2.1").esPosteriorA(Version.de("1.2"))).isTrue();
    }

    @Test
    void ignoraLaVEDelanteYElSufijoDeLasReleases() {
        // GitHub etiqueta las releases como "v1.2.0". Y el jar de desarrollo es
        // "1.1.0-SNAPSHOT", que para comparar vale lo mismo que 1.1.0.
        assertThat(Version.de("v1.2.0").esPosteriorA(Version.de("1.1.0-SNAPSHOT"))).isTrue();
    }

    @Test
    void unaVersionIlegibleNoDisparaNingunaActualizacion() {
        // Preferimos no avisar antes que avisar mal: si el manifiesto viene con
        // basura, la app se queda callada en vez de ofrecer actualizar a algo
        // que no se sabe que es.
        assertThat(Version.de("no-es-una-version").esPosteriorA(Version.de("1.0.0"))).isFalse();
        assertThat(Version.de("2.0.0").esPosteriorA(Version.de("tampoco"))).isFalse();
    }
}
