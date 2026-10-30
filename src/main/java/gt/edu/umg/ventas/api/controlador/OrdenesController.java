package gt.edu.umg.ventas.api.controlador;

import gt.edu.umg.ventas.api.dto.OrdenRequest;
import gt.edu.umg.ventas.api.dto.OrdenResponse;
import gt.edu.umg.ventas.api.servicio.OrdenesApiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ordenes-venta")
public class OrdenesController {
    private final OrdenesApiService servicio;

    public OrdenesController(OrdenesApiService servicio) { this.servicio = servicio; }

    @GetMapping
    public List<OrdenResponse> listar(@RequestParam(required = false) String estado) {
        return servicio.listar(estado);
    }

    @GetMapping("/{id}")
    public OrdenResponse obtener(@PathVariable int id) { return servicio.obtener(id); }

    @PostMapping
    public ResponseEntity<OrdenResponse> crear(@Valid @RequestBody OrdenRequest request) {
        OrdenResponse orden = servicio.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/ordenes-venta/" + orden.id())).body(orden);
    }

    @PutMapping("/{id}")
    public OrdenResponse actualizar(@PathVariable int id, @Valid @RequestBody OrdenRequest request) {
        return servicio.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public OrdenResponse cancelar(@PathVariable int id) { return servicio.cancelar(id); }
}
