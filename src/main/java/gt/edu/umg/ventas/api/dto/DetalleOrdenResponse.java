package gt.edu.umg.ventas.api.dto;

public record DetalleOrdenResponse(long productoId, String codigo, String nombre,
                                  int cantidad, String precioUnitario, String subtotal,
                                  String imagenUrl, String imagenDescripcion) {
    /** Mantiene compatible el contrato Java anterior, sin metadatos de imagen. */
    public DetalleOrdenResponse(long productoId, String codigo, String nombre,
                               int cantidad, String precioUnitario, String subtotal) {
        this(productoId, codigo, nombre, cantidad, precioUnitario, subtotal, null, null);
    }
}