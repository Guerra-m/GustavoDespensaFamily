package com.guerram.DespensaFamily.ia;

import com.guerram.DespensaFamily.dto.ia.ProductoExtraidoDTO;

import java.util.List;

// Interfaz entre el service y el proveedor de IA real. El service de negocio
// (AsistenteCargaProductosService) depende de esto, no de Gemini directamente
// — si el dia de manana Gemini deja de ser viable, se cambia la
// implementacion sin tocar el resto (ver analisis de la etapa de
// planificacion: "alternativa si Gemini deja de ser viable").
public interface ClienteExtraccionIa {

    // texto y/o imagen: al menos uno de los dos llega no nulo (lo valida el
    // service antes de llamar aca). imagenMimeType es el tipo REAL detectado
    // por ValidadorImagenIa, nunca el declarado por el cliente HTTP.
    List<ProductoExtraidoDTO> extraer(String texto, byte[] imagen, String imagenMimeType);
}
