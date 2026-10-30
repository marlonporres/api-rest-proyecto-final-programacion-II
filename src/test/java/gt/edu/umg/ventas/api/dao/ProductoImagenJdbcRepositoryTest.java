package gt.edu.umg.ventas.api.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductoImagenJdbcRepositoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProductoImagenJdbcRepository repositorio = new ProductoImagenJdbcRepository(jdbc);

    @Test
    void aceptaSoloArchivosLocalesYNormalizaBarraInicial() {
        assertEquals("/media/productos/laptop-14.jpg",
                ProductoImagenJdbcRepository.normalizarRuta("media/productos/laptop-14.jpg"));
        assertEquals("/media/productos/RAM_16.webp",
                ProductoImagenJdbcRepository.normalizarRuta("/media/productos/RAM_16.webp"));
    }

    @Test
    void rechazaUrlExternaRecorridoDeRutasSubcarpetasYExtensionesActivas() {
        assertNull(ProductoImagenJdbcRepository.normalizarRuta(null));
        for (String invalida : List.of("https://externo.test/foto.jpg", "//externo.test/foto.jpg",
                "/media/productos/../config.jpg", "/media/productos/%2e%2e.jpg",
                "/media/productos/subcarpeta/foto.jpg", "/media/productos/foto.svg",
                "/media/productos/foto.jpg?x=1", "/media/productos/foto.jpg#x",
                "C:\\media\\productos\\foto.jpg", "/media/productos/foto.jpg\n", "")) {
            assertNull(ProductoImagenJdbcRepository.normalizarRuta(invalida), invalida);
        }
    }

    @Test
    void sinProductosNoConsultaSql() {
        assertTrue(repositorio.buscarPorIds(List.of()).isEmpty());
        assertTrue(repositorio.buscarPorIds(null).isEmpty());
        assertTrue(repositorio.buscarPorIds(List.of(0L, -2L)).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test
    void antesDeMigrarLaAusenciaExplicitaDeTablaDevuelveImagenesVacias() {
        when(jdbc.queryForObject(contains("OBJECT_ID"), eq(Integer.class))).thenReturn(0);
        assertTrue(repositorio.buscarPorIds(List.of(9L, 10L)).isEmpty());
        verify(jdbc).queryForObject(contains("dbo.producto_imagen"), eq(Integer.class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void consultaUnLoteConParametrosYDescartaMetadatosConRutaNoPermitida() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        ResultSet filas = mock(ResultSet.class);
        when(filas.next()).thenReturn(true, true, false);
        when(filas.getLong("id_producto")).thenReturn(9L);
        when(filas.getString("ruta_relativa")).thenReturn("media/productos/laptop.jpg", "https://externo.test/foto.jpg");
        when(filas.getString("texto_alternativo")).thenReturn("Imagen de referencia de laptop");
        doAnswer(llamada -> {
            ResultSetExtractor<?> extractor = llamada.getArgument(1);
            return extractor.extractData(filas);
        }).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        var resultado = repositorio.buscarPorIds(List.of(9L, 10L, 9L));
        assertEquals(Map.of(9L, new ImagenProducto("/media/productos/laptop.jpg", "Imagen de referencia de laptop")), resultado);
        ArgumentCaptor<Object[]> parametros = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(contains("WHERE id_producto IN (?,?)"), any(ResultSetExtractor.class), parametros.capture());
        assertArrayEquals(new Object[]{9L, 10L}, parametros.getValue());
    }

    @Test
    void masDeMilIdsSeResuelvenEnDosLotesYNoUnaConsultaPorProducto() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(Map.of());
        List<Long> ids = new ArrayList<>(LongStream.rangeClosed(1, 1001).boxed().toList());
        ids.add(1L);
        repositorio.buscarPorIds(ids);
        ArgumentCaptor<Object[]> parametros = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).query(anyString(), any(ResultSetExtractor.class), parametros.capture());
        assertEquals(List.of(1000, 1), parametros.getAllValues().stream().map(p -> p.length).toList());
        verify(jdbc, times(1)).queryForObject(anyString(), eq(Integer.class));
    }

    @Test
    void falloRealDeSqlNoSeOcultaComoAusenciaDeImagenes() {
        var fallo = new DataAccessResourceFailureException("Base no disponible");
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenThrow(fallo);
        assertSame(fallo, assertThrows(DataAccessResourceFailureException.class,
                () -> repositorio.buscarPorIds(List.of(9L))));
    }
}