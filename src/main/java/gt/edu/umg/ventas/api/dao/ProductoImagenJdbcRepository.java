package gt.edu.umg.ventas.api.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Lee únicamente metadatos de imágenes: no escribe productos ni descarga archivos. */
@Repository
public class ProductoImagenJdbcRepository implements ProductoImagenRepository {
    private static final int TAMANO_LOTE = 1000;
    private static final String EXISTE_TABLA = "SELECT CASE WHEN OBJECT_ID(N'dbo.producto_imagen', N'U') "
            + "IS NULL THEN 0 ELSE 1 END";
    private static final Pattern RUTA_PERMITIDA = Pattern.compile(
            "^/?media/productos/[A-Za-z0-9][A-Za-z0-9_-]*\\.(?i:jpg|jpeg|png|webp)$");
    private final JdbcTemplate jdbc;

    public ProductoImagenJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, ImagenProducto> buscarPorIds(Collection<Long> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) return Map.of();
        LinkedHashSet<Long> unicos = new LinkedHashSet<>();
        for (Long id : productoIds) {
            if (id != null && id > 0) unicos.add(id);
        }
        if (unicos.isEmpty()) return Map.of();

        // Compatibilidad anterior a la migración: solo esta ausencia permite no tener imágenes.
        // Un error de conexión o una tabla con columnas incorrectas sí se propaga al handler HTTP.
        Integer existe = jdbc.queryForObject(EXISTE_TABLA, Integer.class);
        if (!Integer.valueOf(1).equals(existe)) return Map.of();

        List<Long> ids = new ArrayList<>(unicos);
        Map<Long, ImagenProducto> imagenes = new HashMap<>();
        for (int desde = 0; desde < ids.size(); desde += TAMANO_LOTE) {
            List<Long> lote = ids.subList(desde, Math.min(desde + TAMANO_LOTE, ids.size()));
            String parametros = String.join(",", Collections.nCopies(lote.size(), "?"));
            String sql = "SELECT id_producto, ruta_relativa, texto_alternativo "
                    + "FROM dbo.producto_imagen WHERE id_producto IN (" + parametros + ")";
            Map<Long, ImagenProducto> encontrados = jdbc.query(sql,
                    (ResultSetExtractor<Map<Long, ImagenProducto>>) filas -> {
                        Map<Long, ImagenProducto> resultado = new HashMap<>();
                        while (filas.next()) {
                            String ruta = normalizarRuta(filas.getString("ruta_relativa"));
                            if (ruta != null) {
                                resultado.put(filas.getLong("id_producto"),
                                        new ImagenProducto(ruta, filas.getString("texto_alternativo")));
                            }
                        }
                        return resultado;
                    }, lote.toArray());
            if (encontrados != null) imagenes.putAll(encontrados);
        }
        return Map.copyOf(imagenes);
    }

    static String normalizarRuta(String ruta) {
        if (ruta == null || !RUTA_PERMITIDA.matcher(ruta).matches()) return null;
        return ruta.startsWith("/") ? ruta : "/" + ruta;
    }
}