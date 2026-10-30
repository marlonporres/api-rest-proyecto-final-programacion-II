package gt.edu.umg.ventas.api.controlador;

import gt.edu.umg.ventas.api.servicio.OrdenesApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usa el conversor JSON configurado realmente por Boot, sin conexiones SQL. */
@SpringBootTest
@AutoConfigureMockMvc
class ConfiguracionJsonTest {
    @Autowired MockMvc http;
    @MockitoBean OrdenesApiService servicio;

    @Test
    void cantidadFraccionariaNoSeTruncaSilenciosamente() throws Exception {
        http.perform(post("/api/v1/ordenes-venta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1,\"detalles\":[{\"productoId\":1,\"cantidad\":2.5}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        verifyNoInteractions(servicio);
    }

    @Test
    void preciosOTotalesEnviadosPorElClienteNoFormanParteDeLaPeticion() throws Exception {
        http.perform(post("/api/v1/ordenes-venta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":1,\"total\":\"0.01\",\"detalles\":[{\"productoId\":1,\"cantidad\":2}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        verifyNoInteractions(servicio);
    }
}
