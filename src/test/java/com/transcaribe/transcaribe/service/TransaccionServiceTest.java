package com.transcaribe.transcaribe.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.TransaccionRepository;

@ExtendWith(MockitoExtension.class)
class TransaccionServiceTest {

    @Mock
    private TransaccionRepository repositorioTransacciones;

    @InjectMocks
    private TransaccionService transaccionService;

    @Test
    void soloPermiteConsultarTransaccionesDeLaCuentaPropietaria() {
        Usuario propietario = new Usuario("propietario@example.com", "hash", "Propietario");
        propietario.setId("usuario-1");
        Usuario otroUsuario = new Usuario("otro@example.com", "hash", "Otro");
        otroUsuario.setId("usuario-2");
        Transaccion transaccion = new Transaccion(
                3900.0, LocalDateTime.now(), "PASAJE", propietario);
        transaccion.setId("transaccion-1");
        when(repositorioTransacciones.findById("transaccion-1"))
                .thenReturn(Optional.of(transaccion));

        assertTrue(transaccionService.obtenerTransaccionDeUsuario("transaccion-1", propietario).isPresent());
        assertFalse(transaccionService.obtenerTransaccionDeUsuario("transaccion-1", otroUsuario).isPresent());
    }

    @Test
    void registraLosDatosSegurosDelPagoEnLaTransaccion() {
        Usuario usuario = new Usuario("pasajero@example.com", "hash", "Pasajero");
        when(repositorioTransacciones.save(any(Transaccion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transaccionService.registrarTransaccion(
                usuario, "Recarga Tarjeta: ******7890", 25000,
                "PSE", "pasajero.pse@example.com", "******7890", 10);

        ArgumentCaptor<Transaccion> captor = ArgumentCaptor.forClass(Transaccion.class);
        verify(repositorioTransacciones).save(captor.capture());
        assertNotNull(captor.getValue().getFecha());
        assertEquals("PSE", captor.getValue().getMetodoPago());
        assertEquals("pasajero.pse@example.com", captor.getValue().getCuentaPse());
        assertEquals("******7890", captor.getValue().getTarjetaTranscaribe());
        assertEquals(10, captor.getValue().getCantidadPasajes());
    }
}
