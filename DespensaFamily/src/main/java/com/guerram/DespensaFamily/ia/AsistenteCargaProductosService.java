package com.guerram.DespensaFamily.ia;

import com.guerram.DespensaFamily.config.ReglaDeNegocioException;
import com.guerram.DespensaFamily.dto.ia.ExtraccionProductosResponseDTO;
import com.guerram.DespensaFamily.dto.ia.ProductoExtraidoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Orquesta el flujo "texto/imagen -> IA -> datos estructurados", sin guardar
// nada: esto NUNCA llama a ProductoService.crear. El resultado es solo un
// preview para que el usuario revise/corrija antes de confirmar (ver el
// diagrama de la etapa de planificacion: IA -> preview editable -> usuario
// confirma -> backend valida de nuevo -> recien ahi se guarda).
@Service
@RequiredArgsConstructor
public class AsistenteCargaProductosService {

    // Limite generoso para una lista o el texto de una factura tipeada a
    // mano, bajo para evitar mandar textos absurdamente largos a la API.
    private static final int TEXTO_MAXIMO_CARACTERES = 4000;

    private final ClienteExtraccionIa clienteExtraccionIa;

    public ExtraccionProductosResponseDTO extraer(String texto, byte[] imagen) {
        String textoLimpio = texto != null ? texto.trim() : null;
        boolean hayTexto = textoLimpio != null && !textoLimpio.isBlank();
        boolean hayImagen = imagen != null && imagen.length > 0;

        if (!hayTexto && !hayImagen) {
            throw new ReglaDeNegocioException("Mandá una lista de productos, una foto, o ambos");
        }
        if (hayTexto && textoLimpio.length() > TEXTO_MAXIMO_CARACTERES) {
            throw new ReglaDeNegocioException("El texto es demasiado largo (máximo " + TEXTO_MAXIMO_CARACTERES + " caracteres)");
        }

        String mimeType = null;
        byte[] imagenValidada = null;
        if (hayImagen) {
            var validada = ValidadorImagenIa.validar(imagen);
            imagenValidada = validada.contenido();
            mimeType = validada.mimeType();
        }

        List<ProductoExtraidoDTO> productos = clienteExtraccionIa.extraer(textoLimpio, imagenValidada, mimeType);
        return new ExtraccionProductosResponseDTO(productos);
    }
}
