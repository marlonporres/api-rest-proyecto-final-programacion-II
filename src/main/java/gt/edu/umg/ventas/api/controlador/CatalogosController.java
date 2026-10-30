package gt.edu.umg.ventas.api.controlador;

import gt.edu.umg.ventas.api.dto.ClienteResponse;
import gt.edu.umg.ventas.api.dto.ProductoResponse;
import gt.edu.umg.ventas.api.servicio.OrdenesApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogosController {
    private final OrdenesApiService servicio;

    public CatalogosController(OrdenesApiService servicio) { this.servicio = servicio; }

    @GetMapping("/clientes")
    public List<ClienteResponse> clientes() { return servicio.clientes(); }

    @GetMapping("/productos")
    public List<ProductoResponse> productos() { return servicio.productos(); }
}
