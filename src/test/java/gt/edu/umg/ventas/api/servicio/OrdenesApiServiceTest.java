package gt.edu.umg.ventas.api.servicio;

import gt.edu.umg.ventas.api.dto.DetalleOrdenRequest;
import gt.edu.umg.ventas.api.dao.ImagenProducto;
import gt.edu.umg.ventas.api.dao.ProductoImagenRepository;
import gt.edu.umg.ventas.api.dto.OrdenRequest;
import gt.edu.umg.ventas.api.dto.OrdenResponse;
import gt.edu.umg.ventas.dao.*;
import gt.edu.umg.ventas.modelo.*;
import gt.edu.umg.ventas.servicio.InventarioService;
import gt.edu.umg.ventas.servicio.OrdenVentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrdenesApiServiceTest {
    private final ClienteDAO clientes = mock(ClienteDAO.class);
    private final ProductoDAO productos = mock(ProductoDAO.class);
    private final UsuarioDAO usuarios = mock(UsuarioDAO.class);
    private final OrdenVentaDAO ordenes = mock(OrdenVentaDAO.class);
    private final InventarioService inventario = mock(InventarioService.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-26T18:34:56Z"), ZoneId.of("America/Guatemala"));
    private Cliente cliente;
    private Producto producto;
    private Usuario usuario;

    @BeforeEach
    void datos() {
        cliente = new Cliente(7, "CF", "Cliente de prueba", "", "", "");
        producto = new Producto();
        producto.setIdProducto(9);
        producto.setCodigo("TEC-009");
        producto.setNombre("Producto de prueba");
        producto.setPrecioVenta(new BigDecimal("95.00"));
        usuario = new Usuario();
        usuario.setIdUsuario(4);
        usuario.setActivo(true);
        when(clientes.buscarPorId(7)).thenReturn(cliente);
        when(productos.buscarPorId(9)).thenReturn(producto);
        when(usuarios.buscarPorNombreUsuario("operador-academico")).thenReturn(usuario);
        when(inventario.obtenerDisponibilidadTotal(9)).thenReturn(20);
    }

    private OrdenesApiService api(OrdenVentaService dominio) {
        return new OrdenesApiService(clientes, productos, usuarios, ordenes, inventario,
                dominio, reloj, "operador-academico");
    }

    @Test
    void crearUsaPrecioCatalogoUsuarioServidorFechaGuatemalaYReglasCompartidas() throws Exception {
        OrdenVentaService dominio = new OrdenVentaService(ordenes, inventario);
        doAnswer(invocacion -> {
            OrdenVenta orden = invocacion.getArgument(0);
            orden.setId(31);
            when(ordenes.obtener(31)).thenReturn(orden);
            return null;
        }).when(ordenes).crear(any());

        OrdenResponse respuesta = api(dominio).crear(new OrdenRequest(7L, "  Entrega  ",
                List.of(new DetalleOrdenRequest(9L, 2))));

        assertEquals(31, respuesta.id());
        assertTrue(respuesta.numeroOrden().startsWith("OV-"));
        assertEquals("2026-10-26T12:34:56", respuesta.fecha());
        assertEquals("PENDIENTE", respuesta.estado());
        assertEquals("190.00", respuesta.subtotal());
        assertEquals("22.80", respuesta.iva());
        assertEquals("212.80", respuesta.total());
        assertEquals("Entrega", respuesta.observaciones());
        assertEquals("95.00", respuesta.detalles().getFirst().precioUnitario());
        assertTrue(respuesta.editable());
        assertNull(respuesta.detalles().getFirst().imagenUrl());
        assertNull(respuesta.detalles().getFirst().imagenDescripcion());
        verify(usuarios).buscarPorNombreUsuario("operador-academico");
        verify(ordenes).crear(argThat(o -> o.getUsuario() == usuario));
        verify(inventario, never()).registrarSalida(any(), any(), anyInt(), anyString(), any());
    }

    @Test
    void productosSoloPublicaActivosConDisponibilidadYDecimalesComoCadenas() {
        Producto inactivo = new Producto();
        inactivo.setIdProducto(10);
        inactivo.setActivo(false);
        when(productos.listar()).thenReturn(List.of(inactivo, producto));
        var respuesta = api(mock(OrdenVentaService.class)).productos();
        assertEquals(1, respuesta.size());
        assertEquals("95.00", respuesta.getFirst().precioUnitario());
        assertEquals(20, respuesta.getFirst().disponible());
        assertNull(respuesta.getFirst().imagenUrl());
        assertNull(respuesta.getFirst().imagenDescripcion());
        verify(inventario, never()).obtenerDisponibilidadTotal(10);
    }

    @Test
    void clienteInexistenteNoGuardaOrden() {
        OrdenVentaService dominio = mock(OrdenVentaService.class);
        assertThrows(NoSuchElementException.class, () -> api(dominio).crear(
                new OrdenRequest(88L, "", List.of(new DetalleOrdenRequest(9L, 1)))));
        verifyNoInteractions(dominio);
    }

    @Test
    void productoInactivoYDuplicadoSeRechazanAntesDeGuardar() {
        OrdenVentaService dominio = mock(OrdenVentaService.class);
        assertThrows(IllegalArgumentException.class, () -> api(dominio).crear(new OrdenRequest(7L, "",
                List.of(new DetalleOrdenRequest(9L, 1), new DetalleOrdenRequest(9L, 1)))));
        producto.setActivo(false);
        assertThrows(IllegalStateException.class, () -> api(dominio).crear(new OrdenRequest(7L, "",
                List.of(new DetalleOrdenRequest(9L, 1)))));
        verifyNoInteractions(dominio);
    }

    @Test
    void actorInactivoImpideCrear() {
        usuario.setActivo(false);
        OrdenVentaService dominio = mock(OrdenVentaService.class);
        assertThrows(IllegalStateException.class, () -> api(dominio).crear(new OrdenRequest(7L, "",
                List.of(new DetalleOrdenRequest(9L, 1)))));
        verify(dominio, never()).crearOrdenVenta(any());
    }

    @Test
    void documentoAsociadoDeshabilitaEdicionAunqueOrdenEstePendiente() {
        OrdenVentaService dominio = mock(OrdenVentaService.class);
        when(dominio.obtenerOrden(31)).thenReturn(orden(31, EstadoOrdenVenta.PENDIENTE));
        when(ordenes.tieneDocumentosRelacionados(31)).thenReturn(true);
        OrdenResponse respuesta = api(dominio).obtener(31);
        assertFalse(respuesta.editable());
        assertFalse(respuesta.cancelable());
    }

    @Test
    void listarFiltraOrdenaYConservaCanceladas() {
        when(ordenes.obtenerTodos()).thenReturn(List.of(
                orden(3, EstadoOrdenVenta.CANCELADA), orden(8, EstadoOrdenVenta.PENDIENTE)));
        var servicio = api(mock(OrdenVentaService.class));
        assertEquals(List.of(8, 3), servicio.listar(null).stream().map(OrdenResponse::id).toList());
        assertEquals("CANCELADA", servicio.listar("CANCELADA").getFirst().estado());
        assertThrows(IllegalArgumentException.class, () -> servicio.listar("OTRA"));
    }

    @Test
    void actualizarYCancelarDeleganEnOperacionTransaccionalYRecarganRespuesta() {
        OrdenVentaService dominio = mock(OrdenVentaService.class);
        when(dominio.obtenerOrden(31)).thenReturn(orden(31, EstadoOrdenVenta.PENDIENTE));
        var servicio = api(dominio);
        assertEquals(31, servicio.actualizar(31, new OrdenRequest(7L, "Cambio",
                List.of(new DetalleOrdenRequest(9L, 3)))).id());
        verify(dominio).actualizarOrdenVenta(argThat(o -> o.getId() == 31
                && o.getDetalles().getFirst().getCantidad() == 3));
        when(dominio.obtenerOrden(31)).thenReturn(orden(31, EstadoOrdenVenta.CANCELADA));
        assertEquals("CANCELADA", servicio.cancelar(31).estado());
        verify(dominio).cancelarOrdenVenta(31);
    }

    @Test
    void catalogoIncluyeImagenDisponibleYOpcionalesNulosParaProductoSinImagen() {
        Producto sinImagen = new Producto();
        sinImagen.setIdProducto(10);
        sinImagen.setNombre("Producto sin fotografía");
        sinImagen.setPrecioVenta(new BigDecimal("10.00"));
        when(productos.listar()).thenReturn(List.of(producto, sinImagen));
        ProductoImagenRepository imagenes = mock(ProductoImagenRepository.class);
        when(imagenes.buscarPorIds(anyCollection())).thenReturn(Map.of(9L,
                new ImagenProducto("/media/productos/laptop.jpg", "Laptop de referencia")));
        var api = new OrdenesApiService(clientes, productos, usuarios, ordenes, inventario,
                mock(OrdenVentaService.class), reloj, "operador-academico", imagenes);
        var respuesta = api.productos();
        assertEquals("/media/productos/laptop.jpg", respuesta.getFirst().imagenUrl());
        assertEquals("Laptop de referencia", respuesta.getFirst().imagenDescripcion());
        assertNull(respuesta.get(1).imagenUrl());
        assertNull(respuesta.get(1).imagenDescripcion());
        verify(imagenes, times(1)).buscarPorIds(argThat(ids -> Set.copyOf(ids).equals(Set.of(9L, 10L))));
    }

    @Test
    void listadoDeOrdenesComparteUnaBusquedaDeImagenesParaTodasLasLineas() {
        when(ordenes.obtenerTodos()).thenReturn(List.of(orden(3, EstadoOrdenVenta.CANCELADA),
                orden(8, EstadoOrdenVenta.PENDIENTE)));
        ProductoImagenRepository imagenes = mock(ProductoImagenRepository.class);
        when(imagenes.buscarPorIds(anyCollection())).thenReturn(Map.of(9L,
                new ImagenProducto("/media/productos/laptop.jpg", "Laptop de referencia")));
        var api = new OrdenesApiService(clientes, productos, usuarios, ordenes, inventario,
                mock(OrdenVentaService.class), reloj, "operador-academico", imagenes);
        var respuesta = api.listar(null);
        assertEquals(2, respuesta.size());
        assertTrue(respuesta.stream().allMatch(o -> o.detalles().getFirst().imagenUrl()
                .equals("/media/productos/laptop.jpg")));
        verify(imagenes, times(1)).buscarPorIds(Set.of(9L));
    }

    @Test
    void detalleDeOrdenIncluyeImagenConservandoPreciosYTotales() {
        OrdenVentaService dominio = mock(OrdenVentaService.class);
        when(dominio.obtenerOrden(31)).thenReturn(orden(31, EstadoOrdenVenta.PENDIENTE));
        ProductoImagenRepository imagenes = ids -> Map.of(9L,
                new ImagenProducto("/media/productos/laptop.jpg", "Laptop de referencia"));
        var api = new OrdenesApiService(clientes, productos, usuarios, ordenes, inventario,
                dominio, reloj, "operador-academico", imagenes);
        var respuesta = api.obtener(31);
        assertEquals("/media/productos/laptop.jpg", respuesta.detalles().getFirst().imagenUrl());
        assertEquals("Laptop de referencia", respuesta.detalles().getFirst().imagenDescripcion());
        assertEquals("95.00", respuesta.detalles().getFirst().precioUnitario());
        assertEquals("190.00", respuesta.subtotal());
        assertEquals("212.80", respuesta.total());
    }

    private OrdenVenta orden(int id, EstadoOrdenVenta estado) {
        OrdenVenta orden = new OrdenVenta();
        orden.setId(id);
        orden.setNumeroOrden("OV-" + id);
        orden.setFecha(LocalDateTime.of(2026, 10, 26, 12, 30));
        orden.setCliente(cliente);
        orden.setEstado(estado);
        DetalleOrdenVenta detalle = new DetalleOrdenVenta();
        detalle.setProducto(producto);
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(producto.getPrecioVenta());
        detalle.setOrden(orden);
        orden.agregarDetalle(detalle);
        return orden;
    }
}
