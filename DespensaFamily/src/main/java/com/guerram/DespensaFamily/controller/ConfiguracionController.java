package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.config.ReglaDeNegocioException;
import com.guerram.DespensaFamily.config.PermisosDeLectura;
import com.guerram.DespensaFamily.dto.ConfiguracionDTO;
import com.guerram.DespensaFamily.dto.ia.ClaveIaDTO;
import com.guerram.DespensaFamily.dto.ia.EstadoIaDTO;
import com.guerram.DespensaFamily.service.ConfiguracionIaService;
import com.guerram.DespensaFamily.service.ConfiguracionService;
import com.guerram.DespensaFamily.util.OptimisticRetry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/configuracion")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;
    private final ConfiguracionIaService configuracionIaService;

    // DF-SEC-003: el margen por defecto, junto con el precio de venta, deja
    // sacar el costo de cada producto que lo usa. Quien no ve costos (ver
    // PermisosDeLectura) recibe el resto —nombre del negocio, umbral de stock
    // bajo, que usan Ventas y el menu— sin ese campo.
    @GetMapping
    public ConfiguracionDTO obtener(Authentication quien) {
        ConfiguracionDTO config = configuracionService.obtener();
        if (!PermisosDeLectura.veCostos(quien)) config.setGananciaPorDefecto(null);
        return config;
    }

    @PutMapping
    public ConfiguracionDTO actualizar(@RequestBody ConfiguracionDTO dto) {
        return configuracionService.actualizar(dto);
    }

    @PostMapping("/aplicar-ganancia-global")
    public Map<String, Object> aplicarGananciaGlobal(@RequestBody Map<String, BigDecimal> body) {
        BigDecimal porcentaje = body.get("porcentaje");
        if (porcentaje == null) {
            throw new ReglaDeNegocioException("Falta el porcentaje de ganancia");
        }
        int actualizados = OptimisticRetry.ejecutar(() -> configuracionService.aplicarGananciaATodos(porcentaje));
        return Map.of("productosActualizados", actualizados);
    }

    // Estado de la clave de Gemini (F5). Nunca devuelve la clave en si — ver
    // EstadoIaDTO y SEC-01/SEC-05 de la biblioteca de seguridad. La ruta ya
    // esta restringida a ADMIN de forma explicita en SecurityConfig (no
    // alcanza con la regla general de /configuracion/**, que deja el GET
    // abierto a cualquier autenticado).
    @GetMapping("/ia")
    public EstadoIaDTO obtenerEstadoIa() {
        return configuracionIaService.obtenerEstado();
    }

    @PutMapping("/ia")
    public EstadoIaDTO actualizarClaveIa(@RequestBody ClaveIaDTO dto) {
        return configuracionIaService.actualizar(dto);
    }

    @DeleteMapping("/ia")
    @ResponseStatus(HttpStatus.OK)
    public EstadoIaDTO quitarClaveIa() {
        return configuracionIaService.quitar();
    }
}
