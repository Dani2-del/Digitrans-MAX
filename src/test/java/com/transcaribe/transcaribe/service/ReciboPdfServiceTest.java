package com.transcaribe.transcaribe.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;

class ReciboPdfServiceTest {

    private final ReciboPdfService reciboPdfService = new ReciboPdfService();

    @Test
    void generaPdfConLosDatosDeLaTransaccion() throws Exception {
        Usuario usuario = new Usuario("pasajero@example.com", "hash", "Maria Cartagena");
        usuario.setId("usuario-1");
        Transaccion transaccion = new Transaccion(
                3900.0,
                LocalDateTime.of(2026, 9, 25, 14, 30),
                "PASAJE [1234567890] - Ruta T100E",
                usuario);
        transaccion.setId("transaccion-1");
        transaccion.setMetodoPago("PSE");
        transaccion.setCuentaPse("pasajero.pse@example.com");
        transaccion.setTarjetaTranscaribe("******7890");
        transaccion.setCantidadPasajes(1);

        byte[] pdf = reciboPdfService.generarRecibo(transaccion);

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            String contenido = new PDFTextStripper().getText(document);
            assertTrue(contenido.contains("RECIBO DE TRANSACCION"));
            assertTrue(contenido.contains("pasajero@example.com"));
            assertTrue(contenido.contains("PASAJE [****] - Ruta T100E"));
            assertFalse(contenido.contains("1234567890"));
            assertTrue(contenido.contains("Fecha y hora registrada: 25/09/2026 14:30:00"));
            assertTrue(contenido.contains("Cuenta PSE: pasajero.pse@example.com"));
            assertTrue(contenido.contains("Tarjeta Transcaribe: ******7890"));
            assertTrue(contenido.contains("Cantidad de pasajes: 1"));
            assertTrue(contenido.contains("3.900,00"));
        }
    }
}
