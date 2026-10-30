package gt.edu.umg.ventas.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrdenRequest(
        @NotNull(message = "El cliente es obligatorio.")
        @Positive(message = "El identificador del cliente debe ser positivo.") Long clienteId,
        @Size(max = 500, message = "Las observaciones no pueden exceder 500 caracteres.") String observaciones,
        @NotEmpty(message = "Agregue al menos un producto.")
        List<@NotNull(message = "El detalle no puede ser nulo.") @Valid DetalleOrdenRequest> detalles) {
}
