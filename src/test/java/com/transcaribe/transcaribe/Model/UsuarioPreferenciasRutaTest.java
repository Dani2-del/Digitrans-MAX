package com.transcaribe.transcaribe.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class UsuarioPreferenciasRutaTest {

    @Test
    void cadaRutaFavoritaTienePreferenciaIndependienteYDesactivadaPorDefecto() {
        Usuario usuario = new Usuario("pasajero@example.com", "hash", "Pasajero");
        usuario.agregarRutaFavorita("T100E");
        usuario.agregarRutaFavorita("A102");

        PreferenciaRutaNotificacion predeterminada =
                usuario.obtenerPreferenciaNotificacionRuta("T100E");
        usuario.guardarPreferenciaNotificacionRuta(new PreferenciaRutaNotificacion(
                "T100E", true, LocalTime.of(6, 0), LocalTime.of(8, 0)));

        assertFalse(usuario.obtenerPreferenciaNotificacionRuta("A102").isNotificacionesActivas());
        assertTrue(usuario.obtenerPreferenciaNotificacionRuta("T100E").isNotificacionesActivas());
        assertEquals(LocalTime.of(6, 0), predeterminada.getHoraDesde());

        usuario.eliminarRutaFavorita("T100E");
        assertTrue(usuario.getPreferenciasNotificacionRutas().isEmpty());
    }
}
