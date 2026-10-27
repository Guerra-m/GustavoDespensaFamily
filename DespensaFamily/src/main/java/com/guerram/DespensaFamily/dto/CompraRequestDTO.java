package com.guerram.DespensaFamily.dto;

import com.guerram.DespensaFamily.model.MetodoPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CompraRequestDTO {
    @NotBlank(message = "El proveedor es obligatorio")
    private String proveedor;

    @NotEmpty(message = "La compra debe tener al menos un producto")
    @Valid
    private List<DetalleCompraRequestDTO> detalles;

    // null se trata como true en el servicio (ver CompraService): las
    // compras siguen registrandose "pagadas" por defecto salvo que se marque
    // lo contrario, que es el caso de uso mas comun.
    private Boolean pagada;

    @NotNull(message = "El método de pago es obligatorio")
    private MetodoPago metodoPago;
}
