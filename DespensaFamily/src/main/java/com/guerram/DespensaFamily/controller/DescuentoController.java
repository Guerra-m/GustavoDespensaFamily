package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.DescuentoDTO;
import com.guerram.DespensaFamily.service.DescuentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/descuentos")
@RequiredArgsConstructor
public class DescuentoController {

    private final DescuentoService descuentoService;

    @GetMapping
    public List<DescuentoDTO> listar() {
        return descuentoService.listar();
    }

    @PostMapping
    public DescuentoDTO crear(@Valid @RequestBody DescuentoDTO dto) {
        return descuentoService.crear(dto);
    }

    @PutMapping("/{id}")
    public DescuentoDTO actualizar(@PathVariable Long id, @Valid @RequestBody DescuentoDTO dto) {
        return descuentoService.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        descuentoService.eliminar(id);
    }
}
