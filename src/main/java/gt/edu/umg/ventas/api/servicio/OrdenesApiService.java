package gt.edu.umg.ventas.api.servicio;

import gt.edu.umg.ventas.api.dto.*;
import gt.edu.umg.ventas.api.dao.ImagenProducto;
import gt.edu.umg.ventas.api.dao.ProductoImagenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import gt.edu.umg.ventas.dao.ClienteDAO;
import gt.edu.umg.ventas.dao.OrdenVentaDAO;
import gt.edu.umg.ventas.dao.ProductoDAO;
import gt.edu.umg.ventas.dao.UsuarioDAO;
import gt.edu.umg.ventas.modelo.*;
import gt.edu.umg.ventas.servicio.InventarioService;
import gt.edu.umg.ventas.servicio.OrdenVentaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/** Adaptador HTTP: obtiene los datos autoritativos antes de usar la lógica compartida. */
@Service
public class OrdenesApiService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final ClienteDAO clientes;
    private final ProductoDAO productos;
    private final UsuarioDAO usuarios;
    private final OrdenVentaDAO ordenes;
    private final InventarioService inventario;
    private final OrdenVentaService servicio;
    private final Clock reloj;
    private final String usuarioOperador;
    private final ProductoImagenRepository imagenes;

    /** Constructor compatible para consumidores anteriores sin repositorio de imágenes. */
    public OrdenesApiService(ClienteDAO clientes, ProductoDAO productos, UsuarioDAO usuarios,
                            OrdenVentaDAO ordenes, InventarioService inventario, OrdenVentaService servicio,
                            Clock reloj, String usuarioOperador) {
        this(clientes, productos, usuarios, ordenes, inventario, servicio, reloj,
                usuarioOperador, ids -> Map.of());
    }

    @Autowired
    public OrdenesApiService(ClienteDAO clientes, ProductoDAO productos, UsuarioDAO usuarios,
                            OrdenVentaDAO ordenes, InventarioService inventario, OrdenVentaService servicio,
                            Clock reloj, @Value("${ventas.usuario-operador:admin}") String usuarioOperador,
                            ProductoImagenRepository imagenes) {
        this.clientes = clientes;
        this.productos = productos;
        this.usuarios = usuarios;
        this.ordenes = ordenes;
        this.inventario = inventario;
        this.servicio = servicio;
        this.reloj = reloj;
        this.usuarioOperador = usuarioOperador;
        this.imagenes = imagenes;
    }

    public List<ClienteResponse> clientes() {
        return clientes.listar().stream().map(OrdenesApiService::clienteDto).toList();
    }

    public List<ProductoResponse> productos() {
        List<Producto> activos = productos.listar().stream().filter(Producto::isActivo).toList();
        Map<Long, ImagenProducto> imagenesPorId = imagenes.buscarPorIds(
                activos.stream().map(p -> (long) p.getIdProducto()).toList());
        return activos.stream().map(p -> {
            ImagenProducto imagen = imagenesPorId.get((long) p.getIdProducto());
            return new ProductoResponse(p.getIdProducto(), p.getCodigo(), p.getNombre(),
                    dinero(p.getPrecioVenta()), inventario.obtenerDisponibilidadTotal(p.getIdProducto()),
                    imagen == null ? null : imagen.url(), imagen == null ? null : imagen.descripcion());
        }).toList();
    }

    public List<OrdenResponse> listar(String filtroEstado) {
        EstadoOrdenVenta estado = estadoOpcional(filtroEstado);
        List<OrdenVenta> seleccionadas = ordenes.obtenerTodos().stream()
                .filter(o -> estado == null || o.getEstado() == estado)
                .sorted(Comparator.comparing(OrdenVenta::getId).reversed()).toList();
        Map<Long, ImagenProducto> imagenesPorId = imagenesDeOrdenes(seleccionadas);
        return seleccionadas.stream().map(o -> ordenDto(o, imagenesPorId)).toList();
    }

    public OrdenResponse obtener(int id) {
        validarId(id);
        OrdenVenta orden = servicio.obtenerOrden(id);
        if (orden == null) throw new NoSuchElementException("No existe la orden de venta solicitada.");
        return ordenDto(orden, imagenesDeOrdenes(List.of(orden)));
    }

    public OrdenResponse crear(OrdenRequest request) {
        OrdenVenta orden = propuesta(request);
        Usuario operador = usuarios.buscarPorNombreUsuario(usuarioOperador);
        if (operador == null || !operador.isActivo()) {
            throw new IllegalStateException("El usuario académico configurado no existe o está inactivo.");
        }
        orden.setUsuario(operador);
        orden.setNumeroOrden("OV-" + UUID.randomUUID());
        orden.setFecha(LocalDateTime.now(reloj).withNano(0));
        servicio.crearOrdenVenta(orden);
        return obtener(orden.getId());
    }

    public OrdenResponse actualizar(int id, OrdenRequest request) {
        validarId(id);
        OrdenVenta orden = propuesta(request);
        orden.setId(id);
        servicio.actualizarOrdenVenta(orden);
        return obtener(id);
    }

    public OrdenResponse cancelar(int id) {
        validarId(id);
        servicio.cancelarOrdenVenta(id);
        return obtener(id);
    }

    private OrdenVenta propuesta(OrdenRequest request) {
        if (request == null || request.clienteId() == null || request.clienteId() <= 0) {
            throw new IllegalArgumentException("Seleccione un cliente válido.");
        }
        if (request.detalles() == null || request.detalles().isEmpty()) {
            throw new IllegalArgumentException("Agregue al menos un producto.");
        }
        if (request.observaciones() != null && request.observaciones().length() > 500) {
            throw new IllegalArgumentException("Las observaciones no pueden exceder 500 caracteres.");
        }
        Cliente cliente = clientes.buscarPorId(request.clienteId());
        if (cliente == null) throw new NoSuchElementException("No existe el cliente seleccionado.");
        OrdenVenta orden = new OrdenVenta();
        orden.setCliente(cliente);
        orden.setObservaciones(request.observaciones() == null ? "" : request.observaciones().trim());
        Set<Long> seleccionados = new HashSet<>();
        List<DetalleOrdenVenta> detalles = new ArrayList<>();
        for (DetalleOrdenRequest linea : request.detalles()) {
            if (linea == null || linea.productoId() == null || linea.productoId() <= 0
                    || linea.cantidad() == null || linea.cantidad() <= 0) {
                throw new IllegalArgumentException("Producto y cantidad positiva son obligatorios en cada línea.");
            }
            if (!seleccionados.add(linea.productoId())) {
                throw new IllegalArgumentException("Cada producto debe aparecer una sola vez en la orden.");
            }
            Producto producto = productos.buscarPorId(linea.productoId());
            if (producto == null) throw new NoSuchElementException("No existe uno de los productos seleccionados.");
            if (!producto.isActivo()) throw new IllegalStateException("Uno de los productos seleccionados está inactivo.");
            DetalleOrdenVenta detalle = new DetalleOrdenVenta();
            detalle.setProducto(producto);
            detalle.setCantidad(linea.cantidad());
            detalle.setPrecioUnitario(producto.getPrecioVenta());
            detalle.setOrden(orden);
            detalles.add(detalle);
        }
        orden.setDetalles(detalles);
        return orden;
    }

    private OrdenResponse ordenDto(OrdenVenta orden, Map<Long, ImagenProducto> imagenesPorId) {
        boolean modificable = orden.getEstado() == EstadoOrdenVenta.PENDIENTE
                && !ordenes.tieneDocumentosRelacionados(orden.getId());
        List<DetalleOrdenResponse> detalles = orden.getDetalles().stream()
                .map(d -> detalleDto(d, imagenesPorId.get((long) d.getProducto().getIdProducto())))
                .toList();
        return new OrdenResponse(orden.getId(), orden.getNumeroOrden(), FECHA.format(orden.getFecha()),
                clienteDto(orden.getCliente()), orden.getEstado().name(),
                orden.getObservaciones() == null ? "" : orden.getObservaciones(), detalles,
                dinero(orden.calcularSubtotal()), dinero(orden.calcularImpuesto()), dinero(orden.getTotal()),
                modificable, modificable);
    }

    private Map<Long, ImagenProducto> imagenesDeOrdenes(List<OrdenVenta> seleccionadas) {
        Set<Long> ids = new HashSet<>();
        for (OrdenVenta orden : seleccionadas) {
            for (DetalleOrdenVenta detalle : orden.getDetalles()) {
                ids.add((long) detalle.getProducto().getIdProducto());
            }
        }
        return imagenes.buscarPorIds(ids);
    }

    private static DetalleOrdenResponse detalleDto(DetalleOrdenVenta detalle, ImagenProducto imagen) {
        return new DetalleOrdenResponse(detalle.getProducto().getIdProducto(), detalle.getProducto().getCodigo(),
                detalle.getProducto().getNombre(), detalle.getCantidad(), dinero(detalle.getPrecioUnitario()),
                dinero(detalle.getSubtotal()), imagen == null ? null : imagen.url(),
                imagen == null ? null : imagen.descripcion());
    }

    private static ClienteResponse clienteDto(Cliente cliente) {
        return new ClienteResponse(cliente.getIdCliente(), cliente.getNit(), cliente.getNombre());
    }

    private static EstadoOrdenVenta estadoOpcional(String estado) {
        if (estado == null || estado.isBlank()) return null;
        try {
            return EstadoOrdenVenta.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("El estado debe ser PENDIENTE, COMPLETADA o CANCELADA.");
        }
    }

    private static void validarId(int id) {
        if (id <= 0) throw new IllegalArgumentException("El identificador de la orden debe ser positivo.");
    }

    private static String dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
