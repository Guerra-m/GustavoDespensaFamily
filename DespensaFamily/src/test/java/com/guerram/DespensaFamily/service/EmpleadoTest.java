package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.caja.EstadoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.MovimientoCajaDTO;
import com.guerram.DespensaFamily.dto.empleados.CrearEmpleadoRequestDTO;
import com.guerram.DespensaFamily.dto.empleados.DiaTrabajadoDTO;
import com.guerram.DespensaFamily.dto.empleados.EmpleadoDTO;
import com.guerram.DespensaFamily.dto.empleados.PagarEmpleadoRequestDTO;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Empleados: alcance agregado después del mapa original. Un empleado no es
 * un Usuario (puede no tener cuenta), un día trabajado es una fila y no un
 * booleano, y un pago es un egreso de caja con categoría SUELDO — monto
 * libre, sin período fijo (decisión del dueño).
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class EmpleadoTest {

    @Autowired
    private EmpleadoService empleadoService;

    @Autowired
    private CajaService cajaService;

    private EmpleadoDTO crearEmpleado(String nombre) {
        CrearEmpleadoRequestDTO dto = new CrearEmpleadoRequestDTO();
        dto.setNombre(nombre);
        return empleadoService.crear(dto);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unEmpleadoPuedeExistirSinUsuario() {
        EmpleadoDTO e = crearEmpleado("Repositor de prueba");

        assertThat(e.getId()).isNotNull();
        assertThat(e.isActivo()).isTrue();
        assertThat(e.getUsername()).isNull();
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void marcarUnDiaDosVecesEsIdempotente() {
        EmpleadoDTO e = crearEmpleado("Empleado de prueba dias");
        LocalDate hoy = LocalDate.now();

        empleadoService.marcarDia(e.getId(), hoy);
        empleadoService.marcarDia(e.getId(), hoy); // segunda vez, no tiene que duplicar

        long marcados = empleadoService.diasTrabajados(hoy, hoy).stream()
                .filter(d -> d.getEmpleadoId().equals(e.getId()))
                .count();

        assertThat(marcados).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void desmarcarUnDiaLoSacaDeLaGrilla() {
        EmpleadoDTO e = crearEmpleado("Empleado de prueba desmarcar");
        LocalDate hoy = LocalDate.now();

        empleadoService.marcarDia(e.getId(), hoy);
        empleadoService.desmarcarDia(e.getId(), hoy);

        boolean sigueMarcado = empleadoService.diasTrabajados(hoy, hoy).stream()
                .anyMatch(d -> d.getEmpleadoId().equals(e.getId()));

        assertThat(sigueMarcado).isFalse();
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void desmarcarUnDiaQueNuncaSeMarcoNoRompeNada() {
        EmpleadoDTO e = crearEmpleado("Empleado de prueba desmarcar sin marcar");
        // No deberia tirar excepcion.
        empleadoService.desmarcarDia(e.getId(), LocalDate.now());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void pagarleAUnEmpleadoBajaLaCajaYQuedaConSuCategoriaPropia() {
        EmpleadoDTO e = crearEmpleado("Empleado de prueba pago");
        EstadoCajaDTO antes = cajaService.estadoDeCaja();

        PagarEmpleadoRequestDTO pago = new PagarEmpleadoRequestDTO();
        pago.setMonto(25000);
        pago.setMetodoPago(MetodoPago.EFECTIVO);

        MovimientoCajaDTO movimiento = empleadoService.pagar(e.getId(), pago);

        assertThat(movimiento.getCategoria()).isEqualTo(CategoriaMovimiento.SUELDO);
        assertThat(movimiento.getEmpleadoId()).isEqualTo(e.getId());

        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getSaldoTotal() - antes.getSaldoTotal()).isEqualTo(-25000.0);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unSueldoNoSeCargaComoGastoOperativo() {
        // Si SUELDO se mezclara con GASTO, el reporte de gastos operativos se
        // volveria inutil (el pago mas grande y regular tapando al resto).
        EmpleadoDTO e = crearEmpleado("Empleado de prueba categoria");

        PagarEmpleadoRequestDTO pago = new PagarEmpleadoRequestDTO();
        pago.setMonto(1000);
        pago.setMetodoPago(MetodoPago.EFECTIVO);

        MovimientoCajaDTO movimiento = empleadoService.pagar(e.getId(), pago);

        assertThat(movimiento.getCategoria()).isNotEqualTo(CategoriaMovimiento.GASTO);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void desactivarUnEmpleadoNoLoBorra() {
        EmpleadoDTO e = crearEmpleado("Empleado de prueba desactivar");

        empleadoService.desactivar(e.getId());

        EmpleadoDTO releido = empleadoService.listar().stream()
                .filter(x -> x.getId().equals(e.getId()))
                .findFirst().orElseThrow();

        assertThat(releido.isActivo()).isFalse();
    }
}
