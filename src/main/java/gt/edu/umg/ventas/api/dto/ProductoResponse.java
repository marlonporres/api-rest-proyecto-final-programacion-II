package gt.edu.umg.ventas.api.dto;

public record ProductoResponse(long id, String codigo, String nombre,
                               String precioUnitario, int disponible,
                               String imagenUrl, String imagenDescripcion) {
    /** Mantiene compatible el contrato Java anterior, sin metadatos de imagen. */
    public ProductoResponse(long id, String codigo, String nombre, String precioUnitario, int disponible) {
        this(id, codigo, nombre, precioUnitario, disponible, null, null);
    }
}