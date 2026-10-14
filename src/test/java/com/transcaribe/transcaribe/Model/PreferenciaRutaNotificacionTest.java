package com.transcaribe.transcaribe.Model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class PreferenciaRutaNotificacionTest {

    @Test
    void soloIncluyeHorariosProgramadosDentroDeLaFranjaActiva() {
        PreferenciaRutaNotificacion preferencia = new PreferenciaRutaNotificacion(
                "T100E", true, LocalTime.of(6, 0), LocalTime.of(8, 0));

        assertTrue(preferencia.incluyeHora(LocalTime.of(6, 0)));
        assertTrue(preferencia.incluyeHora(LocalTime.of(7, 30)));
        assertTrue(preferencia.incluyeHora(LocalTime.of(8, 0)));
        assertFalse(preferencia.incluyeHora(LocalTime.of(5, 59)));
        assertFalse(preferencia.incluyeHora(LocalTime.of(8, 1)));
    }

    @Test
    void noIncluyeHorariosCuandoLasNotificacionesEstanDesactivadas() {
        PreferenciaRutaNotificacion preferencia = new PreferenciaRutaNotificacion(
                "T100E", false, LocalTime.of(6, 0), LocalTime.of(8, 0));

        assertFalse(preferencia.incluyeHora(LocalTime.of(7, 0)));
    }
}
