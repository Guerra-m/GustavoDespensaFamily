package com.guerram.DespensaFamily.dto.empleados;

import java.math.BigDecimal;

import com.guerram.DespensaFamily.model.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Monto libre y sin período fijo, por decisión del dueño: no hay tarifa por
 * día ni sueldo fijo cargado en el sistema, así que no hay contra qué medir
 * un porcentaje pagado — se anota cuánto se le dio y cuándo, nada más.
 */
@Getter
@Setter
@NoArgsConstructor
public class PagarEmpleadoRequestDTO {

    @DecimalMin(value = "0.0", inclusive = false, message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotNull(message = "El método de pago es obligatorio")
    private MetodoPago metodoPago;

    private String descripcion;
}
