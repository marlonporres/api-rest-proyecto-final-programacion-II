package gt.edu.umg.ventas.integracion;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuración exclusiva de las pruebas SQL optativas; no forma parte del JAR. */
final class ConfiguracionSqlLocal {
    private ConfiguracionSqlLocal() {}

    static DataSource crearDataSource() throws IOException {
        Properties propiedades = new Properties();
        Path archivo = Path.of("config", "database.properties");
        if (Files.isRegularFile(archivo)) {
            try (InputStream entrada = Files.newInputStream(archivo)) {
                propiedades.load(entrada);
            }
        }
        DriverManagerDataSource origen = new DriverManagerDataSource();
        origen.setUrl(valor("VENTAS_DB_URL", "db.url", propiedades));
        origen.setUsername(valor("VENTAS_DB_USER", "db.user", propiedades));
        origen.setPassword(valor("VENTAS_DB_PASSWORD", "db.password", propiedades));
        return origen;
    }

    private static String valor(String variable, String clave, Properties propiedades) {
        String valor = System.getenv(variable);
        if (valor == null) valor = propiedades.getProperty(clave);
        if (valor == null) {
            throw new IllegalStateException("Configure " + variable + " o " + clave
                    + " en config/database.properties para habilitar la prueba SQL.");
        }
        return valor;
    }
}