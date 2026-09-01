package com.guerram.DespensaFamily.ia;

import com.guerram.DespensaFamily.dto.ia.ExtraccionProductosResponseDTO;
import com.guerram.DespensaFamily.dto.ia.ProductoExtraidoDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsistenteCargaProductosServiceTest {

    // Fake en vez de mock: no hace falta Mockito para verificar "con que
    // argumentos se llamo", con guardar lo recibido alcanza y es mas legible.
    private static class ClienteExtraccionIaFake implements ClienteExtraccionIa {
        String textoRecibido;
        byte[] imagenRecibida;
        String mimeTypeRecibido;
        List<ProductoExtraidoDTO> aDevolver = List.of();

        @Override
        public List<ProductoExtraidoDTO> extraer(String texto, byte[] imagen, String imagenMimeType) {
            this.textoRecibido = texto;
            this.imagenRecibida = imagen;
            this.mimeTypeRecibido = imagenMimeType;
            return aDevolver;
        }
    }

    private static final byte[] JPEG_VALIDO = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};

    @Test
    void rechazaCuandoNoHayTextoNiImagen() {
        var service = new AsistenteCargaProductosService(new ClienteExtraccionIaFake());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.extraer(null, null));
        assertEquals("Mandá una lista de productos, una foto, o ambos", ex.getMessage());
    }

    @Test
    void rechazaCuandoElTextoEsSoloEspacios() {
        var service = new AsistenteCargaProductosService(new ClienteExtraccionIaFake());
        assertThrows(RuntimeException.class, () -> service.extraer("   ", null));
    }

    @Test
    void rechazaTextoDemasiadoLargo() {
        var service = new AsistenteCargaProductosService(new ClienteExtraccionIaFake());
        String textoLargo = "a".repeat(4001);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.extraer(textoLargo, null));
        assertEquals("El texto es demasiado largo (máximo 4000 caracteres)", ex.getMessage());
    }

    @Test
    void rechazaUnaImagenQueNoEsRealmenteUnaImagen() {
        var service = new AsistenteCargaProductosService(new ClienteExtraccionIaFake());
        byte[] archivoFalso = "esto no es una imagen".getBytes();
        assertThrows(RuntimeException.class, () -> service.extraer(null, archivoFalso));
    }

    @Test
    void soloTextoLlegaAlClienteConImagenNula() {
        var fake = new ClienteExtraccionIaFake();
        var service = new AsistenteCargaProductosService(fake);

        service.extraer("  Coca Cola 2.25L  ", null);

        assertEquals("Coca Cola 2.25L", fake.textoRecibido); // se recorta el espacio
        assertNull(fake.imagenRecibida);
        assertNull(fake.mimeTypeRecibido);
    }

    @Test
    void unaImagenValidaLlegaAlClienteConSuMimeTypeReal() {
        var fake = new ClienteExtraccionIaFake();
        var service = new AsistenteCargaProductosService(fake);

        service.extraer(null, JPEG_VALIDO);

        assertEquals("image/jpeg", fake.mimeTypeRecibido);
        assertNull(fake.textoRecibido);
    }

    @Test
    void devuelveLosProductosQueDaElCliente() {
        var fake = new ClienteExtraccionIaFake();
        fake.aDevolver = List.of(new ProductoExtraidoDTO(
                "Sprite 2.25L", "Sprite", "2.25L", "u", "Gaseosas", null, null, "MEDIA"
        ));
        var service = new AsistenteCargaProductosService(fake);

        ExtraccionProductosResponseDTO respuesta = service.extraer("Sprite 2.25L", null);

        assertEquals(1, respuesta.getProductos().size());
        assertEquals("Sprite 2.25L", respuesta.getProductos().get(0).getNombre());
        assertNull(respuesta.getProductos().get(0).getPrecio()); // nunca inventado
    }
}
