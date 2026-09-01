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
            throw new RuntimeException("El archivo no es una imagen JPEG, PNG, WEBP o HEIC valida");
        }
        return new ImagenValidada(contenido, mimeType);
    }

    // Delega en el detector compartido (ver util/TipoDeImagen) para que la
    // subida de fotos de producto y esto usen exactamente el mismo criterio.
    private static String detectarMimeTypeReal(byte[] b) {
        String tipo = com.guerram.DespensaFamily.util.TipoDeImagen.detectar(b);
        // GIF queda afuera a proposito: Gemini no lo lista entre los formatos
        // que acepta para imagenes inline.
        return com.guerram.DespensaFamily.util.TipoDeImagen.GIF.equals(tipo) ? null : tipo;
    }
}
