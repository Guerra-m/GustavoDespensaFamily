package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.ia.ExtraccionProductosResponseDTO;
import com.guerram.DespensaFamily.ia.AsistenteCargaProductosService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// Asistente de carga de productos: texto y/o foto entran, la IA devuelve un
// preview estructurado, NUNCA se guarda nada desde acá. El alta real sigue
// pasando por POST /productos de siempre, con sus mismas validaciones.
@RestController
@RequestMapping("/ia")
@RequiredArgsConstructor
public class AsistenteIaController {

    private final AsistenteCargaProductosService asistenteCargaProductosService;

    @PostMapping(value = "/productos/extraer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtraccionProductosResponseDTO extraerProductos(
            @RequestPart(value = "texto", required = false) String texto,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen
    ) throws IOException {
        byte[] contenidoImagen = (imagen != null && !imagen.isEmpty()) ? imagen.getBytes() : null;
        return asistenteCargaProductosService.extraer(texto, contenidoImagen);
    }
}
