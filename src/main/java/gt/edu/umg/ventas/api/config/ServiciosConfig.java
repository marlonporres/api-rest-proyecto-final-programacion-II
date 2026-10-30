package gt.edu.umg.ventas.api.config;

import gt.edu.umg.ventas.dao.*;
import gt.edu.umg.ventas.servicio.InventarioService;
import gt.edu.umg.ventas.servicio.OrdenVentaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.ZoneId;

/** Comparte los servicios JDBC existentes sin iniciar la aplicación Swing. */
@Configuration
public class ServiciosConfig {
    @Bean
    public ClienteDAO clienteDAO(DataSource dataSource) {
        ConexionBD.configurarDataSource(dataSource);
        return new ClienteDAOImpl();
    }

    @Bean
    public ProductoDAO productoDAO(DataSource dataSource) {
        ConexionBD.configurarDataSource(dataSource);
        return new ProductoDAOImpl();
    }

    @Bean
    public UsuarioDAO usuarioDAO(DataSource dataSource) {
        ConexionBD.configurarDataSource(dataSource);
        return new UsuarioDAOImpl();
    }

    @Bean
    public OrdenVentaDAO ordenVentaDAO(DataSource dataSource) {
        ConexionBD.configurarDataSource(dataSource);
        return new OrdenVentaDAOImpl();
    }

    @Bean
    public InventarioService inventarioService(OrdenVentaDAO conexionPreparada) {
        return new InventarioService();
    }

    @Bean
    public OrdenVentaService ordenVentaService(OrdenVentaDAO ordenDAO, InventarioService inventario) {
        return new OrdenVentaService(ordenDAO, inventario);
    }

    @Bean
    public Clock relojVentas() {
        return Clock.system(ZoneId.of("America/Guatemala"));
    }
}
