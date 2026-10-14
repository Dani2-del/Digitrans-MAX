package com.transcaribe.transcaribe.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class TarjetaSistemaTransporteTest {

    @Test
    void conservaElSistemaSeleccionadoEnLaTarjeta() {
        Usuario usuario = new Usuario("pasajero@example.com", "hash", "Pasajero");

        usuario.agregarTarjeta("1234567890", BigDecimal.ZERO, SistemaTransporte.TRANSMILENIO);

        assertEquals(SistemaTransporte.TRANSMILENIO,
                usuario.getTarjetas().get(0).getSistemaTransporte());
    }

    @Test
    void mantieneTranscaribeComoSistemaPredeterminadoParaTarjetasExistentes() {
        Tarjeta tarjeta = new Tarjeta("1234567890", BigDecimal.ZERO);
        tarjeta.setSistemaTransporte(null);

        assertEquals(SistemaTransporte.TRANSCARIBE, tarjeta.getSistemaTransporte());
    }
}
