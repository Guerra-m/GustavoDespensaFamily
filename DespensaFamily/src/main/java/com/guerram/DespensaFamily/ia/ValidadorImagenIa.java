package com.guerram.DespensaFamily.ia;

// Valida una imagen ANTES de gastar una llamada saliente a Gemini con ella.
// Sigue UPL-01 (limite de tamano) y UPL-02 (tipo real por magic bytes, nunca
// por el nombre ni el Content-Type que declaro el cliente) de la biblioteca
// de seguridad: la imagen no se guarda en ningun lado (no aplican UPL-04 en
// adelante, que son sobre archivos servidos de vuelta), pero igual no hay que
// confiar en lo que el multipart dice que es.
public final class ValidadorImagenIa {

    // Gemini no necesita mas resolucion que esto para leer una etiqueta o una
    // factura, y un limite bajo corta de raiz el caso "alguien manda un video
    // de 200MB al campo de imagen".
    public static final long TAMANO_MAXIMO_BYTES = 8L * 1024 * 1024; // 8 MB

    private ValidadorImagenIa() {
    }

    public record ImagenValidada(byte[] contenido, String mimeType) {
    }

    public static ImagenValidada validar(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new RuntimeException("La imagen esta vacia");
        }
        if (contenido.length > TAMANO_MAXIMO_BYTES) {
            throw new RuntimeException("La imagen supera el tamano maximo permitido (8 MB)");
        }

        String mimeType = detectarMimeTypeReal(contenido);
        if (mimeType == null) {
            throw new RuntimeException("El archivo no es una imagen JPEG, PNG o WEBP valida");
        }
        return new ImagenValidada(contenido, mimeType);
    }

    // Firma real de los primeros bytes, no la extension ni el Content-Type
    // declarado (eso es lo que UPL-02 pide verificar: el atacante controla
    // los dos, no el contenido).
    private static String detectarMimeTypeReal(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
