package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.dto.VentaDetalleResponseDTO;
import com.guerram.DespensaFamily.dto.VentaRequestDTO;
import com.guerram.DespensaFamily.service.VentaService;
import com.guerram.DespensaFamily.util.OptimisticRetry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;

    @PostMapping
    public VentaDTO registrarVenta(@Valid  @RequestBody VentaRequestDTO request) {
        return OptimisticRetry.ejecutar(
                () -> ventaService.registrarVenta(
                        request.getDetalles(), request.getMetodoPago(), request.getCliente()));
    }

    @GetMapping
    public List<VentaDTO> listarVentas() {
        return ventaService.listarVentas();
    }
    @GetMapping("/{id}")
    public VentaDTO buscarVentaPorId(@PathVariable Long id) {
        return ventaService.buscarPorId(id);
    }
    @GetMapping("/{id}/detalle")
    public VentaDetalleResponseDTO buscarVentaConDetalle(@PathVariable Long id) {
        return ventaService.buscarDetallePorId(id);
    }
    @DeleteMapping("/{id}")
    public void anularVenta(@PathVariable Long id) {
        OptimisticRetry.ejecutar(() -> ventaService.anularVenta(id));
    }

}
