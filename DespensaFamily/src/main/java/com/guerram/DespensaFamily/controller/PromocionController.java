package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.PromocionDTO;
import com.guerram.DespensaFamily.service.PromocionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/promociones")
@RequiredArgsConstructor
public class PromocionController {

    private final PromocionService promocionService;

    @GetMapping
    public List<PromocionDTO> listar() {
        return promocionService.listar();
    }

    @PostMapping
    public PromocionDTO crear(@Valid @RequestBody PromocionDTO dto) {
        return promocionService.crear(dto);
    }

    @PutMapping("/{id}")
    public PromocionDTO actualizar(@PathVariable Long id, @Valid @RequestBody PromocionDTO dto) {
        return promocionService.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        promocionService.eliminar(id);
    }
}
