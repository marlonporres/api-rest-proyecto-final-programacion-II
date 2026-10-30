package gt.edu.umg.ventas.api.controlador;

import gt.edu.umg.ventas.api.dao.EstadoConexionDAO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SaludController {
    private final EstadoConexionDAO conexion;

    public SaludController(EstadoConexionDAO conexion) { this.conexion = conexion; }

    @GetMapping("/salud")
    public Map<String, String> salud() throws SQLException {
        conexion.comprobar();
        return Map.of("estado", "OK", "baseDatos", "DISPONIBLE");
    }
}
