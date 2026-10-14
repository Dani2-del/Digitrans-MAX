package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.GeometriaRuta;
import com.transcaribe.transcaribe.Repository.GeometriaRutaRepository;
import com.transcaribe.transcaribe.dto.GuardarGeometriaRutaRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeometriaRutaServiceTest {

    private final GeometriaRutaRepository repository = mock(GeometriaRutaRepository.class);
    private final GeometriaRutaService service = new GeometriaRutaService(repository);

    @Test
    void guardaTrazadoDeRutaTroncalConCodigoAlfanumerico() {
        when(repository.findByCodigoRutaAndSentido("T100E", "IDA")).thenReturn(Optional.empty());
        when(repository.save(any(GeometriaRuta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GeometriaRuta geometria = service.guardar(" t100e ", "ida", request(
                point(10.4, -75.5),
                point(10.5, -75.6)
        ));

        assertEquals("T100E", geometria.getCodigoRuta());
        assertEquals("IDA", geometria.getSentido());
        assertEquals(2, geometria.getCoordenadas().size());
        assertEquals(-75.6, geometria.getCoordenadas().get(1).longitud());
        verify(repository).save(geometria);
    }

    @Test
    void rechazaSentidoDesconocido() {
        assertThrows(ResponseStatusException.class,
                () -> service.guardar("X103", "NORTE", request(
                        point(10.4, -75.5),
                        point(10.5, -75.6)
                )));
    }

    @Test
    void rechazaCoordenadasFueraDeRango() {
        assertThrows(ResponseStatusException.class,
                () -> service.guardar("X103", "IDA", request(
                        point(91, -75.5),
                        point(10.5, -75.6)
                )));
    }

    @Test
    void rechazaTrazadosConMenosDeDosPuntos() {
        assertThrows(ResponseStatusException.class,
                () -> service.guardar("X103", "IDA", request(point(10.4, -75.5))));
    }

    private static GuardarGeometriaRutaRequest request(
            GuardarGeometriaRutaRequest.CoordenadaRequest... points) {
        GuardarGeometriaRutaRequest request = new GuardarGeometriaRutaRequest();
        request.setCoordenadas(List.of(points));
        return request;
    }

    private static GuardarGeometriaRutaRequest.CoordenadaRequest point(double latitude, double longitude) {
        GuardarGeometriaRutaRequest.CoordenadaRequest point =
                new GuardarGeometriaRutaRequest.CoordenadaRequest();
        point.setLatitud(latitude);
        point.setLongitud(longitude);
        return point;
    }
}
