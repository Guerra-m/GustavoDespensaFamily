package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.caja.MovimientoCajaDTO;
import com.guerram.DespensaFamily.dto.empleados.CrearEmpleadoRequestDTO;
import com.guerram.DespensaFamily.dto.empleados.DiaTrabajadoDTO;
import com.guerram.DespensaFamily.dto.empleados.EmpleadoDTO;
import com.guerram.DespensaFamily.dto.empleados.PagarEmpleadoRequestDTO;
import com.guerram.DespensaFamily.service.EmpleadoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Empleados: pagos y días trabajados. Solo ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/empleados")
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpleadoDTO crear(@Valid @RequestBody CrearEmpleadoRequestDTO request) {
        return empleadoService.crear(request);
    }

    @GetMapping
    public List<EmpleadoDTO> listar() {
        return empleadoService.listar();
    }

    @DeleteMapping("/{id}")
    public void desactivar(@PathVariable Long id) {
        empleadoService.desactivar(id);
    }

    /** La grilla semanal: todos los días marcados en el rango, de cualquier empleado. */
    @GetMapping("/dias")
    public List<DiaTrabajadoDTO> diasTrabajados(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta) {
        return empleadoService.diasTrabajados(desde, hasta);
    }

    @PostMapping("/{id}/dias/{fecha}")
    public void marcarDia(@PathVariable Long id, @PathVariable LocalDate fecha) {
        empleadoService.marcarDia(id, fecha);
    }

    @DeleteMapping("/{id}/dias/{fecha}")
    public void desmarcarDia(@PathVariable Long id, @PathVariable LocalDate fecha) {
        empleadoService.desmarcarDia(id, fecha);
    }

    /** Monto libre, sin período fijo (decisión del dueño — ver PagarEmpleadoRequestDTO). */
    @PostMapping("/{id}/pagar")
    public MovimientoCajaDTO pagar(@PathVariable Long id, @Valid @RequestBody PagarEmpleadoRequestDTO request) {
        return empleadoService.pagar(id, request);
    }
}
