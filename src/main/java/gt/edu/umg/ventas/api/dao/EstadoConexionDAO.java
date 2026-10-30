package gt.edu.umg.ventas.api.dao;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Repository
public class EstadoConexionDAO {
    private final DataSource dataSource;

    public EstadoConexionDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void comprobar() throws SQLException {
        try (Connection con = dataSource.getConnection();
             Statement statement = con.createStatement()) {
            statement.setQueryTimeout(3);
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new SQLException("No se pudo comprobar la conexión.", "08000");
                }
            }
        }
    }
}
