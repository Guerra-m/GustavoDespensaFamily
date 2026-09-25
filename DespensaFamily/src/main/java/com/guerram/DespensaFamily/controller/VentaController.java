package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.config.PermisosDeLectura;
import com.guerram.DespensaFamily.dto.CotizacionDTO;
import com.guerram.DespensaFamily.dto.CotizarRequestDTO;
import com.guerram.DespensaFamily.dto.PaginaDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.dto.VentaDetalleResponseDTO;
import com.guerram.DespensaFamily.dto.VentaRequestDTO;
import com.guerram.DespensaFamily.service.LecturaDeVentasPorRol;
import com.guerram.DespensaFamily.service.VentaService;
import com.guerram.DespensaFamily.util.OptimisticRetry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;
    private final LecturaDeVentasPorRol lecturaPorRol;

    // DF-BUG-003: header opcional. El frontend manda un UUID por intento de
    // confirmar (mismo si reintenta el mismo carrito tras un error de red,
    // nuevo despues de exito o si cambia el carrito) — ver ventas.ts. Sin
    // header, se comporta exactamente como antes (ninguna deduplicacion).
    @PostMapping
    public VentaDTO registrarVenta(@Valid @RequestBody VentaRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            return OptimisticRetry.ejecutar(
                    () -> ventaService.registrarVenta(
                            request.getDetalles(), request.getMetodoPago(), request.getCliente(), idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            // Dos peticiones con la MISMA clave llegaron tan juntas que las
            // dos pasaron la lectura previa (ver VentaService) antes de que
            // cualquiera hiciera commit. La unique constraint de V12 dejo
            // pasar solo una; esta busca la que gano y la devuelve en vez de
            // propagar el 500 que daria el conflicto crudo de la base.
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                return ventaService.buscarPorClaveIdempotencia(idempotencyKey).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    // DF-BUG-001: el carrito confirma el total ANTES de cobrar, sin tocar
    // stock ni caja. Comparte el calculo con registrarVenta (ver
    // VentaService.calcularLinea): no hay una segunda cuenta en ningun lado.
    @PostMapping("/cotizar")
    public CotizacionDTO cotizar(@Valid @RequestBody CotizarRequestDTO request) {
        return ventaService.cotizar(request.getDetalles());
    }

    // DF-SEC-003: sin PERM_CAJA (ver PermisosDeLectura) cada GET contesta con
    // las ventas propias del dia y nada mas — ver LecturaDeVentasPorRol.
    @GetMapping
    public List<VentaDTO> listarVentas(Authentication quien) {
        if (!PermisosDeLectura.veTodasLasVentas(quien)) return lecturaPorRol.deHoy(quien);
        return ventaService.listarVentas();
    }

    // DF-PERF-001: endpoint NUEVO, no reemplaza GET /ventas -- ese sigue
    // devolviendo la lista completa (home.ts y cualquier otro consumidor que
    // ya cuenta con eso no se tocan). Los tres parametros de fecha son
    // opcionales; sin ninguno, trae la pagina 0 de 50 ventas mas recientes.
    //
    // DF-SEC-003: misma regla que GET /ventas -- sin PERM_CAJA, ignora
    // desde/hasta (no puede pedir "todo el año pasado") y pagina solo sobre
    // sus propias ventas del dia via LecturaDeVentasPorRol.
    @GetMapping("/pagina")
    public PaginaDTO<VentaDTO> listarVentasPaginado(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication quien) {
        if (!PermisosDeLectura.veTodasLasVentas(quien)) return lecturaPorRol.deHoyPaginado(quien, page, size);
        return ventaService.listarVentasPaginado(desde, hasta, page, size);
    }
    @GetMapping("/{id}")
    public VentaDTO buscarVentaPorId(@PathVariable Long id, Authentication quien) {
        if (!PermisosDeLectura.veTodasLasVentas(quien)) return lecturaPorRol.unaDeHoy(id, quien);
        return ventaService.buscarPorId(id);
    }
    @GetMapping("/{id}/detalle")
    public VentaDetalleResponseDTO buscarVentaConDetalle(@PathVariable Long id, Authentication quien) {
        if (!PermisosDeLectura.veTodasLasVentas(quien)) return lecturaPorRol.detalleDeHoy(id, quien);
        return lecturaPorRol.segunQuien(ventaService.buscarDetallePorId(id), quien);
    }
    @DeleteMapping("/{id}")
    public void anularVenta(@PathVariable Long id) {
        OptimisticRetry.ejecutar(() -> ventaService.anularVenta(id));
    }

}
