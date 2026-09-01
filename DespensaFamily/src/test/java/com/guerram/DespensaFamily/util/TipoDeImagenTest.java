package com.guerram.DespensaFamily.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Detecta el tipo real de una imagen por su contenido.
 *
 * <p>Existe porque habia dos criterios distintos para el mismo riesgo: el
 * asistente de IA miraba los magic bytes y la subida de fotos de producto
 * confiaba en el Content-Type que declaraba el cliente — que lo controla quien
 * sube el archivo (UPL-02).
 */
class TipoDeImagenTest {

    @Test
    void reconoceJpeg() {
        assertEquals(TipoDeImagen.JPEG, TipoDeImagen.detectar(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0}));
    }

    @Test
    void reconocePng() {
        assertEquals(TipoDeImagen.PNG, TipoDeImagen.detectar(
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
    }

    @Test
    void reconoceWebp() {
        assertEquals(TipoDeImagen.WEBP, TipoDeImagen.detectar(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
    }

    @Test
    void reconoceGif() {
        assertEquals(TipoDeImagen.GIF, TipoDeImagen.detectar(
                new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0}));
    }

    @Test
    void reconoceHeicDeIphone() {
        assertEquals(TipoDeImagen.HEIC, TipoDeImagen.detectar(
                new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'}));
    }

    @Test
    void unMp4NoEsUnaImagen() {
        // Mismo contenedor ISO-BMFF que el HEIC: si se mirara solo "ftyp",
        // entraria un video por el campo de la foto.
        assertNull(TipoDeImagen.detectar(
                new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}));
    }

    @Test
    void unTextoQueDiceLlamarseJpgNoEsUnaImagen() {
        assertNull(TipoDeImagen.detectar("esto no es una imagen".getBytes()));
    }

    @Test
    void noSeRompeConEntradasVaciasONulas() {
        // El caller usa el resultado como clave de un Map.of(), que tira
        // NullPointerException con clave nula: el null tiene que llegar limpio
        // para que lo chequee, no en forma de excepcion desde aca.
        assertNull(TipoDeImagen.detectar(null));
        assertNull(TipoDeImagen.detectar(new byte[0]));
        assertNull(TipoDeImagen.detectar(new byte[]{1, 2}));
    }
}
