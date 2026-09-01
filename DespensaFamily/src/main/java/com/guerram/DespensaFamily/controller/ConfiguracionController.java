package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.ConfiguracionDTO;
import com.guerram.DespensaFamily.service.ConfiguracionService;
import com.guerram.DespensaFamily.util.OptimisticRetry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/configuracion")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    @GetMapping
    public ConfiguracionDTO obtener() {
        return configuracionService.obtener();
    }

    @PutMapping
    public ConfiguracionDTO actualizar(@RequestBody ConfiguracionDTO dto) {
        return configuracionService.actualizar(dto);
    }

    @PostMapping("/aplicar-ganancia-global")
    public Map<String, Object> aplicarGananciaGlobal(@RequestBody Map<String, Double> body) {
        Double porcentaje = body.get("porcentaje");
        if (porcentaje == null) {
            throw new RuntimeException("Falta el porcentaje de ganancia");
        }
        int actualizados = OptimisticRetry.ejecutar(() -> configuracionService.aplicarGananciaATodos(porcentaje));
        return Map.of("productosActualizados", actualizados);
    }
}
