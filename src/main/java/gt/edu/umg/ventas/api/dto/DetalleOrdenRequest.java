package gt.edu.umg.ventas.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DetalleOrdenRequest(
        @NotNull(message = "El producto es obligatorio.")
        @Positive(message = "El identificador del producto debe ser positivo.") Long productoId,
        @NotNull(message = "La cantidad es obligatoria.")
        @Positive(message = "La cantidad debe ser un entero positivo.") Integer cantidad) {
}
