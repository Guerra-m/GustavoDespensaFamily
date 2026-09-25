package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.AnularCompraResponseDTO;
import com.guerram.DespensaFamily.dto.CompraCotizacionDTO;
import com.guerram.DespensaFamily.dto.CompraCotizarRequestDTO;
import com.guerram.DespensaFamily.dto.CompraDTO;
import com.guerram.DespensaFamily.dto.CompraDetalleResponseDTO;
import com.guerram.DespensaFamily.dto.CompraRequestDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraDTO;
import com.guerram.DespensaFamily.dto.PaginaDTO;
import com.guerram.DespensaFamily.service.CompraService;
import com.guerram.DespensaFamily.util.OptimisticRetry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;

    @PostMapping
    public CompraDTO registrarCompra(@Valid  @RequestBody CompraRequestDTO request) {
        return OptimisticRetry.ejecutar(() -> compraService.registrarCompra(request));
    }


    // DF-RISK-001: lo que costaria cada linea si se confirmara la compra
    // ahora, sin registrar nada. Comparte la formula con registrarCompra
    // (ver CompraService.previsualizar) — mismo patron que POST
    // /ventas/cotizar.
    @PostMapping("/previsualizar")
    public CompraCotizacionDTO previsualizar(@Valid @RequestBody CompraCotizarRequestDTO request) {
        return compraService.previsualizar(request.getDetalles());
    }

    @GetMapping
    public List<CompraDTO> listarCompras() {
        return compraService.listarCompras();
    }

    // DF-PERF-001: endpoint NUEVO, no reemplaza GET /compras. Mismo control
    // de acceso que el resto de /compras/** (SecurityConfig: ROLE_ADMIN o
    // PERM_STOCK) -- no hace falta nada especial aca para DF-SEC-003.
    @GetMapping("/pagina")
    public PaginaDTO<CompraDTO> listarComprasPaginado(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return compraService.listarComprasPaginado(desde, hasta, page, size);
    }
    @GetMapping("/{id}")
    public CompraDTO buscarCompraPorId(@PathVariable Long id) {
        return compraService.buscarPorId(id);
    }
    @GetMapping("/{id}/detalle")
    public CompraDetalleResponseDTO buscarCompraConDetalle(@PathVariable Long id) {
        return compraService.buscarDetallePorId(id);
    }


    // Solo ADMIN, igual que anular una venta (ver SecurityConfig: todo
    // /compras salvo GET ya es ADMIN).
    @DeleteMapping("/{id}")
    public AnularCompraResponseDTO anularCompra(@PathVariable Long id) {
        return OptimisticRetry.ejecutar(() -> compraService.anularCompra(id));
    }

    /** Marca como pagada una compra que había quedado a cuenta (Fase 11). */
    @PostMapping("/{id}/pagar")
    public CompraDTO marcarPagada(@PathVariable Long id) {
        return compraService.marcarPagada(id);
    }
}
