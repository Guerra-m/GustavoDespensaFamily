package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.caja.ArqueoRequestDTO;
import com.guerram.DespensaFamily.dto.caja.ArqueoResultadoDTO;
import com.guerram.DespensaFamily.dto.caja.EstadoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.MovimientoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.RegistrarMovimientoRequestDTO;
import com.guerram.DespensaFamily.service.CajaService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/caja")
@RequiredArgsConstructor
public class CajaController {

    private final CajaService cajaService;

    /**
     * Saldo, totales de hoy y ultimos movimientos. Lo puede ver el empleado
     * (ver SecurityConfig): el que atiende necesita saber con que efectivo
     * cuenta para dar vuelto.
     */
    @GetMapping
    public EstadoCajaDTO estado() {
        return cajaService.estadoDeCaja();
    }

    @GetMapping("/ultimos")
    public List<MovimientoCajaDTO> ultimos() {
        return cajaService.ultimosMovimientos();
    }

    /** Listado con filtro de fecha. Solo ADMIN: expone gastos y retiros del negocio. */
    @GetMapping("/movimientos")
    public List<MovimientoCajaDTO> movimientos(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        return cajaService.listarMovimientos(desde, hasta);
    }

    /** Carga manual de GASTO, RETIRO, APORTE o AJUSTE. Solo ADMIN. */
    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoCajaDTO registrar(@Valid @RequestBody RegistrarMovimientoRequestDTO request) {
        return cajaService.registrarMovimientoManual(request);
    }

    /** Revierte un movimiento con uno de signo contrario. Solo ADMIN. */
    @PostMapping("/movimientos/{id}/revertir")
    public MovimientoCajaDTO revertir(@PathVariable Long id) {
        return cajaService.revertirMovimiento(id);
    }

    /** Cuenta el cajón y ajusta la diferencia contra lo esperado (Fase 6). Solo ADMIN. */
    @PostMapping("/arqueo")
    public ArqueoResultadoDTO arqueo(@Valid @RequestBody ArqueoRequestDTO request) {
        return cajaService.registrarArqueo(request);
    }
}
