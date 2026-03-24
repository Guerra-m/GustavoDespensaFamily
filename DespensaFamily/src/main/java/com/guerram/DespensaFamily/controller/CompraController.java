package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.CompraDTO;
import com.guerram.DespensaFamily.dto.CompraDetalleResponseDTO;
import com.guerram.DespensaFamily.dto.CompraRequestDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraDTO;
import com.guerram.DespensaFamily.service.CompraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;

    @PostMapping
    public CompraDTO registrarCompra(@Valid  @RequestBody CompraRequestDTO request) {
        return compraService.registrarCompra(request);
    }


    @GetMapping
    public List<CompraDTO> listarCompras() {
        return compraService.listarCompras();
    }
    @GetMapping("/{id}")
    public CompraDTO buscarCompraPorId(@PathVariable Long id) {
        return compraService.buscarPorId(id);
    }
    @GetMapping("/{id}/detalle")
    public CompraDetalleResponseDTO buscarCompraConDetalle(@PathVariable Long id) {
        return compraService.buscarDetallePorId(id);
    }

}
