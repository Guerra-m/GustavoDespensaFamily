package com.guerram.DespensaFamily.util;

import java.nio.charset.StandardCharsets;

/**
 * Que tipo de imagen es un archivo, mirando su contenido.
 *
 * <p>Nunca por la extension ni por el {@code Content-Type} declarado: los dos
 * los controla quien sube el archivo (UPL-02). Antes el asistente de IA hacia
 * esto bien y la subida de fotos de producto no —confiaba en el Content-Type—,
 * asi que habia dos criterios distintos para el mismo riesgo. Esta clase es el
 * unico lugar donde se decide; cada caller elige despues que tipos acepta.
 */
public final class TipoDeImagen {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";
    public static final String GIF = "image/gif";
    public static final String HEIC = "image/heic";
    public static final String HEIF = "image/heif";

    private TipoDeImagen() {
    }

    /** El tipo real, o null si el contenido no es una imagen conocida. */
    public static String detectar(byte[] b) {
        if (b == null) {
            return null;
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return PNG;
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return WEBP;
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F'
                && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return GIF;
        }
        return heicOHeif(b);
    }

    /**
     * HEIC/HEIF: lo que sacan los iPhone por defecto.
     *
     * <p>La firma esta en la caja "ftyp" del contenedor ISO-BMFF (bytes 4 a 7)
     * seguida de la marca mayor (bytes 8 a 11). Mirar solo "ftyp" no alcanza:
     * mp4 y mov usan el mismo contenedor, y dejaria entrar un video al campo
     * de la foto.
     */
    private static String heicOHeif(byte[] b) {
        if (b.length < 12) {
            return null;
        }
        if (!(b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p')) {
            return null;
        }
        String marca = new String(b, 8, 4, StandardCharsets.US_ASCII);
        return switch (marca) {
            case "heic", "heix", "hevc", "hevx" -> HEIC;
            case "mif1", "msf1" -> HEIF;
            default -> null; // isom, mp42, qt… son video
        };
    }
}
