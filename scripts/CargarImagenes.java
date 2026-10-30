import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Administracion explicita: no pertenece al JAR ni se ejecuta al iniciar Spring Boot. */
public final class CargarImagenes {
    private static final String PREFIJO = "/media/productos/";
    private static final List<Tabla> TABLAS = List.of(
        new Tabla("dbo.usuario", "id_usuario"),
        new Tabla("dbo.cliente", "id_cliente"),
        new Tabla("dbo.cliente_telefono", "id_telefono"),
        new Tabla("dbo.categoria", "id_categoria"),
        new Tabla("dbo.producto", "id_producto"),
        new Tabla("dbo.Bodega", "id"),
        new Tabla("dbo.ExistenciaInventario", "id"),
        new Tabla("dbo.OrdenVenta", "id"),
        new Tabla("dbo.DetalleOrdenVenta", "id"),
        new Tabla("dbo.Despacho", "id"),
        new Tabla("dbo.DetalleDespacho", "id"),
        new Tabla("dbo.MovimientoInventario", "id"),
        new Tabla("dbo.factura", "id_factura"),
        new Tabla("dbo.detalle_factura", "id_detalle"),
        new Tabla("dbo.pago", "id_pago")
    );
    record Tabla(String nombre, String clave) {}
    record Huella(long filas, String sha256) {}
    record Imagen(String codigo, String ruta, String alternativo, String fuente, String licencia) {}
    record DatosImagen(String ruta, String alternativo, String fuente, String licencia) {}

    public static void main(String[] args) {
        if (args.length == 1 && (args[0].equals("--ayuda") || args[0].equals("-h") || args[0].equals("-Ayuda"))) {
            System.out.println("Uso: scripts\\cargar-imagenes.cmd [-Aplicar] [-ManifestPath <ruta>] [-RepositorioLocal <ruta>] [-JdbcJar <ruta>] [-Ayuda]");
            System.out.println("Por defecto simula dentro de una transaccion SERIALIZABLE y hace rollback al terminar. -Aplicar confirma los cambios.");
            return;
        }
        try {
            if (args.length != 3 || !(args[0].equals("--aplicar") || args[0].equals("--simular"))) {
                throw new IllegalArgumentException("Argumentos invalidos. Use scripts/cargar-imagenes.cmd.");
            }
            boolean aplicar = args[0].equals("--aplicar");
            Path configuracion = Path.of(args[1]);
            Path migracion = Path.of(args[2]);
            List<Imagen> imagenes = leerEntrada();
            if (imagenes.isEmpty()) throw new IllegalArgumentException("El manifiesto no contiene imagenes.");
            Properties propiedades = new Properties();
            if (Files.isRegularFile(configuracion)) {
                try (var entrada = Files.newInputStream(configuracion)) { propiedades.load(entrada); }
            }
            String url = configuracion("VENTAS_DB_URL", "db.url", propiedades);
            String usuario = configuracion("VENTAS_DB_USER", "db.user", propiedades);
            String clave = configuracion("VENTAS_DB_PASSWORD", "db.password", propiedades);
            try (Connection con = DriverManager.getConnection(url, usuario, clave)) {
                con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                con.setAutoCommit(false);
                try {
                    try (Statement sentencia = con.createStatement()) {
                        sentencia.execute("SET XACT_ABORT ON; SET LOCK_TIMEOUT 10000;");
                    }
                    Map<String, Huella> antes = fotografiar(con);
                    try (Statement sentencia = con.createStatement()) {
                        sentencia.execute(Files.readString(migracion, StandardCharsets.UTF_8));
                    }
                    int insertadas = 0, actualizadas = 0, iguales = 0, protegidas = 0, sinImagen = 0;
                    for (Imagen imagen : imagenes) {
                        long id = buscarProducto(con, imagen.codigo());
                        if (imagen.ruta().isEmpty()) {
                            sinImagen++;
                            continue;
                        }
                        DatosImagen existente = buscarImagen(con, id);
                        if (existente != null && !esRutaAdministrada(existente.ruta())) {
                            protegidas++;
                            System.out.println("IMAGEN_PERSONALIZADA_CONSERVADA codigo=" + imagen.codigo());
                            continue;
                        }
                        DatosImagen nueva = new DatosImagen(imagen.ruta(), imagen.alternativo(), imagen.fuente(), imagen.licencia());
                        if (nueva.equals(existente)) { iguales++; continue; }
                        guardar(con, id, imagen, existente != null);
                        if (existente == null) insertadas++; else actualizadas++;
                    }
                    Map<String, Huella> despues = fotografiar(con);
                    if (!antes.equals(despues)) {
                        throw new IllegalStateException("Cambio inesperado en datos comerciales; se revierte toda la carga.");
                    }
                    if (aplicar) con.commit(); else con.rollback();
                    System.out.println(aplicar ? "CARGA_IMAGENES_CONFIRMADA" : "SIMULACION_REVERTIDA_SIN_CAMBIOS");
                    System.out.printf(Locale.ROOT,
                        "manifest=%d conImagen=%d sinImagen=%d insertadas=%d actualizadas=%d sinCambios=%d personalizadasConservadas=%d%n",
                        imagenes.size(), imagenes.size() - sinImagen, sinImagen, insertadas, actualizadas, iguales, protegidas);
                    for (var entrada : antes.entrySet()) {
                        Huella hAntes = entrada.getValue();
                        Huella hDespues = despues.get(entrada.getKey());
                        System.out.printf(Locale.ROOT, "INTEGRIDAD tabla=%s filasAntes=%d filasDespues=%d sha256=%s intacta=%s%n",
                            entrada.getKey(), hAntes.filas(), hDespues.filas(), hAntes.sha256(), hAntes.equals(hDespues));
                    }
                    System.out.println("DATOS_COMERCIALES_INTACTOS tablas=" + antes.size());
                } catch (Exception error) {
                    try { con.rollback(); } catch (SQLException falloRollback) { error.addSuppressed(falloRollback); }
                    throw error;
                }
            }
        } catch (SQLException error) {
            System.err.printf(Locale.ROOT, "CARGA_IMAGENES_ERROR_SQL estado=%s codigo=%d. No se confirmo la transaccion; revise esquema, conexion y permisos.%n",
                error.getSQLState(), error.getErrorCode());
            System.exit(1);
        } catch (Exception error) {
            // Los mensajes propios contienen solo validaciones del manifiesto, nunca credenciales.
            String detalle = error instanceof IllegalArgumentException || error instanceof IllegalStateException
                ? error.getMessage() : "No se pudo leer o validar la configuracion, el manifiesto o la migracion.";
            System.err.println("CARGA_IMAGENES_ERROR: " + detalle);
            System.exit(1);
        }
    }

    private static String configuracion(String variable, String propiedad, Properties archivo) {
        String valor = System.getenv(variable);
        if (valor == null) valor = archivo.getProperty(propiedad);
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("Falta configuracion de " + variable + ".");
        return valor;
    }

    private static List<Imagen> leerEntrada() throws Exception {
        List<Imagen> resultado = new ArrayList<>();
        Set<String> codigos = new HashSet<>();
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) continue;
                String[] campos = linea.split("\\t", -1);
                if (campos.length != 5) throw new IllegalArgumentException("Formato de entrada invalido.");
                String[] valores = new String[5];
                for (int i = 0; i < 5; i++) valores[i] = new String(Base64.getDecoder().decode(campos[i]), StandardCharsets.UTF_8);
                Imagen imagen = new Imagen(valores[0], valores[1], valores[2], valores[3], valores[4]);
                if (imagen.codigo().isBlank() || imagen.codigo().length() > 50 || imagen.codigo().chars().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException("Codigo de producto invalido.");
                if (!codigos.add(imagen.codigo().toUpperCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Codigo repetido en el manifiesto: " + imagen.codigo());
                if (imagen.ruta().isEmpty()) {
                    if (!imagen.fuente().isEmpty() || !imagen.licencia().isEmpty()) {
                        throw new IllegalArgumentException("Entrada sin ruta no debe declarar fuente o licencia aislada para " + imagen.codigo());
                    }
                    resultado.add(imagen);
                    continue;
                }
                if (!esRutaAdministrada(imagen.ruta())) throw new IllegalArgumentException("Ruta de imagen invalida para " + imagen.codigo());
                if (imagen.alternativo().isBlank() || imagen.alternativo().length() > 250)
                    throw new IllegalArgumentException("Texto alternativo invalido para " + imagen.codigo());
                if (imagen.fuente().isBlank() || imagen.fuente().length() > 1000 || !esUrlWeb(imagen.fuente()))
                    throw new IllegalArgumentException("Fuente URL invalida para " + imagen.codigo());
                if (imagen.licencia().isBlank() || imagen.licencia().length() > 100)
                    throw new IllegalArgumentException("Licencia invalida para " + imagen.codigo());
                resultado.add(imagen);
            }
        }
        if (resultado.stream().allMatch(img -> img.ruta().isEmpty())) {
            throw new IllegalArgumentException("El manifiesto no contiene fotografias locales para cargar.");
        }
        return resultado;
    }

    private static boolean esUrlWeb(String valor) {
        try {
            var uri = java.net.URI.create(valor);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException error) { return false; }
    }

    private static boolean esRutaAdministrada(String ruta) {
        return ruta != null && ruta.length() <= 255
            && ruta.matches("/media/productos/[A-Za-z0-9][A-Za-z0-9_-]{0,119}\\.(?i:jpg|jpeg|png|webp)");
    }

    private static long buscarProducto(Connection con, String codigo) throws SQLException {
        try (PreparedStatement consulta = con.prepareStatement("SELECT id_producto FROM dbo.producto WITH (UPDLOCK,HOLDLOCK) WHERE codigo = ?")) {
            consulta.setString(1, codigo);
            try (ResultSet resultado = consulta.executeQuery()) {
                if (!resultado.next()) throw new IllegalArgumentException("Producto inexistente: " + codigo + ". No se crean productos.");
                long id = resultado.getLong(1);
                if (resultado.next()) throw new IllegalStateException("Codigo de producto ambiguo: " + codigo);
                return id;
            }
        }
    }

    private static DatosImagen buscarImagen(Connection con, long id) throws SQLException {
        try (PreparedStatement consulta = con.prepareStatement("SELECT ruta_relativa,texto_alternativo,fuente_url,licencia FROM dbo.producto_imagen WITH (UPDLOCK,HOLDLOCK) WHERE id_producto = ?")) {
            consulta.setLong(1, id);
            try (ResultSet resultado = consulta.executeQuery()) {
                return resultado.next() ? new DatosImagen(resultado.getString(1), resultado.getString(2), resultado.getString(3), resultado.getString(4)) : null;
            }
        }
    }

    private static void guardar(Connection con, long id, Imagen imagen, boolean actualizar) throws SQLException {
        String sql = actualizar
            ? "UPDATE dbo.producto_imagen SET ruta_relativa=?,texto_alternativo=?,fuente_url=?,licencia=? WHERE id_producto=?"
            : "INSERT INTO dbo.producto_imagen(ruta_relativa,texto_alternativo,fuente_url,licencia,id_producto) VALUES(?,?,?,?,?)";
        try (PreparedStatement sentencia = con.prepareStatement(sql)) {
            sentencia.setNString(1, imagen.ruta());
            sentencia.setNString(2, imagen.alternativo());
            sentencia.setNString(3, imagen.fuente());
            sentencia.setNString(4, imagen.licencia());
            sentencia.setLong(5, id);
            if (sentencia.executeUpdate() != 1) throw new IllegalStateException("La imagen no pudo guardarse de forma univoca.");
        }
    }

    private static Map<String, Huella> fotografiar(Connection con) throws Exception {
        Map<String, Huella> resultado = new LinkedHashMap<>();
        for (Tabla tabla : TABLAS) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long filas = 0;
            // Nombres constantes internos, nunca derivados del JSON o de argumentos externos.
            String sql = "SELECT * FROM " + tabla.nombre() + " WITH (TABLOCK,HOLDLOCK) ORDER BY " + tabla.clave();
            try (Statement sentencia = con.createStatement(); ResultSet datos = sentencia.executeQuery(sql)) {
                ResultSetMetaData meta = datos.getMetaData();
                for (int columna = 1; columna <= meta.getColumnCount(); columna++) {
                    agregar(digest, meta.getColumnName(columna));
                    agregar(digest, meta.getColumnTypeName(columna));
                }
                while (datos.next()) {
                    filas++;
                    for (int columna = 1; columna <= meta.getColumnCount(); columna++) agregar(digest, datos.getString(columna));
                }
            }
            resultado.put(tabla.nombre(), new Huella(filas, HexFormat.of().formatHex(digest.digest())));
        }
        return resultado;
    }

    private static void agregar(MessageDigest digest, String valor) {
        if (valor == null) { digest.update((byte) 0); return; }
        digest.update((byte) 1);
        byte[] bytes = valor.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }
}