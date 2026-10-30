package gt.edu.umg.ventas.api.controlador;

import gt.edu.umg.ventas.api.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ErroresApiHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ErroresApiHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException error) {
        String mensaje = error.getBindingResult().getAllErrors().stream()
                .map(e -> e.getDefaultMessage()).findFirst().orElse("Revise los datos enviados.");
        return respuesta(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", mensaje);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> peticionInvalida(Exception error) {
        return respuesta(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Revise el formato y los valores de la petición.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> metodoInvalido(HttpRequestMethodNotSupportedException error) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", "El método HTTP no está permitido para esta ruta.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> manejar(Exception error) {
        SQLException sql = causaSql(error);
        if (sql != null) {
            if (sql instanceof SQLTransientConnectionException || sql.getSQLState() == null
                    || sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("28")) {
                LOG.warn("No fue posible conectar con SQL Server (tipo {}).", sql.getClass().getSimpleName());
                return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "BASE_NO_DISPONIBLE", "La base de datos no está disponible. Intente nuevamente.");
            }
            LOG.error("Falló una operación de persistencia (estado SQL {}).", sql.getSQLState());
            return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "No se pudo completar la operación.");
        }
        Throwable negocio = causaDeNegocio(error);
        if (negocio instanceof NoSuchElementException) {
            return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", negocio.getMessage());
        }
        if (negocio instanceof IllegalArgumentException) {
            return respuesta(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", negocio.getMessage());
        }
        if (negocio instanceof IllegalStateException) {
            return respuesta(HttpStatus.CONFLICT, "OPERACION_NO_PERMITIDA", negocio.getMessage());
        }
        LOG.error("Error no controlado en la API: {}.", error.getClass().getSimpleName());
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "No se pudo completar la operación.");
    }

    private static SQLException causaSql(Throwable error) {
        Throwable actual = error;
        for (int i = 0; actual != null && i < 25; i++, actual = actual.getCause()) {
            if (actual instanceof SQLException sql) return sql;
        }
        return null;
    }

    // Los DAO existentes preservan la causa de negocio al revertir una transacción.
    private static Throwable causaDeNegocio(Throwable error) {
        Throwable actual = error;
        for (int i = 0; actual != null && i < 25; i++, actual = actual.getCause()) {
            if (actual instanceof NoSuchElementException || actual instanceof IllegalArgumentException
                    || actual instanceof IllegalStateException) return actual;
        }
        return null;
    }

    private static ResponseEntity<ApiError> respuesta(HttpStatus estado, String codigo, String mensaje) {
        return ResponseEntity.status(estado).body(new ApiError(codigo, mensaje));
    }
}
