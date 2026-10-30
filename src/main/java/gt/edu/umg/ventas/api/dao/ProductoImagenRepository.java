package gt.edu.umg.ventas.api.dao;

import java.util.Collection;
import java.util.Map;

/** Consulta en lote para enriquecer DTO sin búsquedas por cada producto o detalle. */
public interface ProductoImagenRepository {
    Map<Long, ImagenProducto> buscarPorIds(Collection<Long> productoIds);
}