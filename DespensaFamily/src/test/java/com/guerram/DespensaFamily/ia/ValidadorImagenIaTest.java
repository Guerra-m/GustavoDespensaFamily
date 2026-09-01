package com.guerram.DespensaFamily.ia;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidadorImagenIaTest {

    @Test
    void aceptaUnJpegValidoPorSusMagicBytes() {
        byte[] jpeg = concatenar(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, ceros(100));
        var resultado = ValidadorImagenIa.validar(jpeg);
        assertEquals("image/jpeg", resultado.mimeType());
    }

    @Test
    void aceptaUnPngValidoPorSusMagicBytes() {
        byte[] png = concatenar(
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
                ceros(100)
        );
        var resultado = ValidadorImagenIa.validar(png);
        assertEquals("image/png", resultado.mimeType());
    }

    @Test
    void aceptaUnWebpValidoPorSusMagicBytes() {
        byte[] webp = concatenar(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'},
                ceros(50)
        );
        var resultado = ValidadorImagenIa.validar(webp);
        assertEquals("image/webp", resultado.mimeType());
    }

    // El caso central de UPL-02: un archivo que dice ser una imagen (por
    // nombre o Content-Type, que este test ni siquiera recibe como parametro
    // a proposito) pero cuyo contenido real es otra cosa.
    @Test
    void rechazaUnArchivoQueNoEsUnaImagenDeVerdad() {
        byte[] scriptDisfrazado = "<?php system($_GET['c']); ?>".getBytes();
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ValidadorImagenIa.validar(scriptDisfrazado));
        assertEquals("El archivo no es una imagen JPEG, PNG, WEBP o HEIC valida", ex.getMessage());
    }

    @Test
    void rechazaUnaImagenQueSuperaElTamanoMaximo() {
        byte[] jpegEnorme = concatenar(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                ceros((int) ValidadorImagenIa.TAMANO_MAXIMO_BYTES + 1)
        );
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ValidadorImagenIa.validar(jpegEnorme));
        assertEquals("La imagen supera el tamano maximo permitido (8 MB)", ex.getMessage());
    }

    @Test
    void rechazaContenidoVacio() {
        assertThrows(RuntimeException.class, () -> ValidadorImagenIa.validar(new byte[0]));
        assertThrows(RuntimeException.class, () -> ValidadorImagenIa.validar(null));
    }

    private static byte[] ceros(int n) {
        return new byte[n];
    }

    private static byte[] concatenar(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    @Test
    void aceptaUnHeicDeIphonePorSusMagicBytes() {
        // Gemini acepta HEIC y HEIF, y es lo que sacan los iPhone por defecto:
        // rechazarlos dejaba afuera justo las fotos que mas se iban a subir.
        byte[] heic = new byte[]{
                0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0
        };
        assertEquals("image/heic", ValidadorImagenIa.validar(heic).mimeType());
    }

    @Test
    void aceptaUnHeifPorSusMagicBytes() {
        byte[] heif = new byte[]{
                0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'i', 'f', '1', 0, 0, 0, 0
        };
        assertEquals("image/heif", ValidadorImagenIa.validar(heif).mimeType());
    }

    @Test
    void unMp4TambienEmpiezaConFtypYNoTieneQuePasar() {
        // La caja "ftyp" es de la familia ISO-BMFF, que incluye video. Mirar
        // solo "ftyp" dejaria entrar un mp4 de 8 MB al campo de la foto.
        byte[] mp4 = new byte[]{
                0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0
        };
        assertThrows(RuntimeException.class, () -> ValidadorImagenIa.validar(mp4));
    }
}
