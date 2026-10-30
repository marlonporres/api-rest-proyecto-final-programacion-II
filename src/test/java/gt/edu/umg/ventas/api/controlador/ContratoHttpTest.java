package gt.edu.umg.ventas.api.controlador;

import gt.edu.umg.ventas.api.dao.EstadoConexionDAO;
import gt.edu.umg.ventas.api.dto.*;
import gt.edu.umg.ventas.api.servicio.OrdenesApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContratoHttpTest {
    private final OrdenesApiService servicio = mock(OrdenesApiService.class);
    private final EstadoConexionDAO estadoConexion = mock(EstadoConexionDAO.class);
    private MockMvc http;
    private static final String VALIDA = """
            {"clienteId":7,"observaciones":"Entrega","detalles":[{"productoId":9,"cantidad":2}]}
            """;

    @BeforeEach
    void configurar() {
        http = MockMvcBuilders.standaloneSetup(new OrdenesController(servicio),
                        new CatalogosController(servicio), new SaludController(estadoConexion))
                .setControllerAdvice(new ErroresApiHandler()).build();
    }

    @Test
    void saludSoloEsExitosaDespuesDeComprobarLaBase() throws Exception {
        http.perform(get("/api/v1/salud")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OK"))
                .andExpect(jsonPath("$.baseDatos").value("DISPONIBLE"));
        verify(estadoConexion).comprobar();
    }

    @Test
    void saludSinBaseEs503ConMensajeSeguro() throws Exception {
        doThrow(new SQLTransientConnectionException("password=secreto;server=privado"))
                .when(estadoConexion).comprobar();
        http.perform(get("/api/v1/salud")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("BASE_NO_DISPONIBLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))));
    }

    @Test
    void crearResponde201LocationYContratoSinCiclos() throws Exception {
        when(servicio.crear(any())).thenReturn(respuesta("PENDIENTE", true));
        http.perform(post("/api/v1/ordenes-venta").contentType(MediaType.APPLICATION_JSON).content(VALIDA))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/ordenes-venta/31"))
                .andExpect(jsonPath("$.cliente.id").value(7))
                .andExpect(jsonPath("$.detalles[0].productoId").value(9))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value("95.00"))
                .andExpect(jsonPath("$.detalles[0].orden").doesNotExist())
                .andExpect(jsonPath("$.iva").value("22.80"))
                .andExpect(jsonPath("$.total").value("212.80"))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void peticionSinClienteODetallesSeRechazaAntesDelServicio() throws Exception {
        http.perform(post("/api/v1/ordenes-venta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        verifyNoInteractions(servicio);
    }

    @Test
    void cantidadCeroSeRechazaAntesDelServicio() throws Exception {
        http.perform(post("/api/v1/ordenes-venta").contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDA.replace("\"cantidad\":2", "\"cantidad\":0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void idConFormatoInvalidoEs400YNoFalloInterno() throws Exception {
        http.perform(get("/api/v1/ordenes-venta/no-es-un-id"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void recursosInexistentesSon404YConflictos409() throws Exception {
        when(servicio.obtener(77)).thenThrow(new NoSuchElementException("No existe la orden."));
        http.perform(get("/api/v1/ordenes-venta/77")).andExpect(status().isNotFound());
        when(servicio.actualizar(eq(31), any())).thenThrow(new IllegalStateException("La orden ya tiene un despacho."));
        http.perform(put("/api/v1/ordenes-venta/31").contentType(MediaType.APPLICATION_JSON).content(VALIDA))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje").value("La orden ya tiene un despacho."));
    }

    @Test
    void conflictoRevalidadoEnDaoConRollbackMantieneRespuesta409() throws Exception {
        when(servicio.crear(any())).thenThrow(new RuntimeException("Error al guardar la orden",
                new IllegalStateException("El producto ya no está activo. Actualice el catálogo.")));
        http.perform(post("/api/v1/ordenes-venta").contentType(MediaType.APPLICATION_JSON).content(VALIDA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("OPERACION_NO_PERMITIDA"))
                .andExpect(jsonPath("$.mensaje").value("El producto ya no está activo. Actualice el catálogo."));
    }

    @Test
    void cancelarConservaRepresentacionYResponde200EnReintentos() throws Exception {
        when(servicio.cancelar(31)).thenReturn(respuesta("CANCELADA", false));
        for (int i = 0; i < 2; i++) {
            http.perform(delete("/api/v1/ordenes-venta/31")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADA"))
                    .andExpect(jsonPath("$.cancelable").value(false));
        }
        verify(servicio, times(2)).cancelar(31);
    }

    @Test
    void catalogosYFiltroUsanNombresQueEsperaAndroid() throws Exception {
        when(servicio.clientes()).thenReturn(List.of(new ClienteResponse(7, "CF", "Cliente")));
        when(servicio.productos()).thenReturn(List.of(new ProductoResponse(9, "TEC-009", "Producto", "95.00", 20)));
        when(servicio.listar("CANCELADA")).thenReturn(List.of(respuesta("CANCELADA", false)));
        http.perform(get("/api/v1/clientes")).andExpect(jsonPath("$[0].nit").value("CF"));
        http.perform(get("/api/v1/productos")).andExpect(jsonPath("$[0].disponible").value(20))
                .andExpect(jsonPath("$[0].precioUnitario").value("95.00"));
        http.perform(get("/api/v1/ordenes-venta").param("estado", "CANCELADA"))
                .andExpect(jsonPath("$[0].estado").value("CANCELADA"));
    }

    @Test
    void catalogoYDetallePublicanMetadatosOpcionalesSinCambiarCamposAnteriores() throws Exception {
        when(servicio.productos()).thenReturn(List.of(
                new ProductoResponse(9, "TEC-009", "Laptop", "95.00", 20,
                        "/media/productos/laptop.jpg", "Laptop de referencia"),
                new ProductoResponse(10, "TEC-010", "Sin imagen", "10.00", 2)));
        http.perform(get("/api/v1/productos")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imagenUrl").value("/media/productos/laptop.jpg"))
                .andExpect(jsonPath("$[0].imagenDescripcion").value("Laptop de referencia"))
                .andExpect(jsonPath("$[1].imagenUrl").hasJsonPath())
                .andExpect(jsonPath("$[1].imagenUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].imagenDescripcion").value(org.hamcrest.Matchers.nullValue()));
        OrdenResponse anterior = respuesta("PENDIENTE", true);
        when(servicio.obtener(31)).thenReturn(new OrdenResponse(anterior.id(), anterior.numeroOrden(),
                anterior.fecha(), anterior.cliente(), anterior.estado(), anterior.observaciones(),
                List.of(new DetalleOrdenResponse(9, "TEC-009", "Laptop", 2, "95.00", "190.00",
                        "/media/productos/laptop.jpg", "Laptop de referencia")),
                anterior.subtotal(), anterior.iva(), anterior.total(), anterior.editable(), anterior.cancelable()));
        http.perform(get("/api/v1/ordenes-venta/31")).andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles[0].imagenUrl").value("/media/productos/laptop.jpg"))
                .andExpect(jsonPath("$.detalles[0].imagenDescripcion").value("Laptop de referencia"))
                .andExpect(jsonPath("$.total").value("212.80"));
    }

    private OrdenResponse respuesta(String estado, boolean editable) {
        return new OrdenResponse(31, "OV-ejemplo", "2026-10-26T12:34:56",
                new ClienteResponse(7, "CF", "Cliente"), estado, "Entrega",
                List.of(new DetalleOrdenResponse(9, "TEC-009", "Producto", 2, "95.00", "190.00")),
                "190.00", "22.80", "212.80", editable, editable);
    }
}
