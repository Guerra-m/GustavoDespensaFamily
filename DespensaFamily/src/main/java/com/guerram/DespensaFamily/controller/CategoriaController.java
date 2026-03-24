package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.CategoriaDTO;
import com.guerram.DespensaFamily.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @PostMapping
    public CategoriaDTO crear(@Valid  @RequestBody CategoriaDTO dto) {
        return categoriaService.crearCategoria(dto);
    }

    @GetMapping
    public List<CategoriaDTO> listar() {
        return categoriaService.listarCategorias();
    }
    @PutMapping("/{id}")
    public CategoriaDTO actualizar(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaDTO dto
    ) {
        return categoriaService.actualizarCategoria(id, dto);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        categoriaService.eliminarCategoria(id);
    }
}
