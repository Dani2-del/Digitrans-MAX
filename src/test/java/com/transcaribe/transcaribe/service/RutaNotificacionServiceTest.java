package com.transcaribe.transcaribe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.transcaribe.transcaribe.Model.HorarioConductor;
import com.transcaribe.transcaribe.Model.PreferenciaRutaNotificacion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RutaNotificacionServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Test
    void notificaCuandoRutaFavoritaYProgramadaSoloDifierenEnMayusculas() {
        List<String> correosEnviados = new ArrayList<>();
        List<String> sentidosNotificados = new ArrayList<>();
        EmailService emailService = new EmailService(null) {
            @Override
            public void enviarNotificacionRutaIniciada(
                    String destinatario, String nombre, String ruta, String sentido,
                    String placaBus, String horaProgramada) {
                correosEnviados.add(destinatario);
                sentidosNotificados.add(sentido);
            }
        };
        Usuario usuario = new Usuario("pasajero@example.com", "hash", "Pasajero");
        usuario.agregarRutaFavorita("C001");
        usuario.guardarPreferenciaNotificacionRuta(new PreferenciaRutaNotificacion(
                "C001", true, LocalTime.of(6, 0), LocalTime.of(8, 0)));
        when(usuarioRepository.findByRutaFavoritaIgnoreCase("^\\s*\\Qc001\\E\\s*$"))
                .thenReturn(List.of(usuario));

        RutaNotificacionService service = new RutaNotificacionService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "usuarioRepository", usuarioRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "emailService", emailService);
        HorarioConductor horario = new HorarioConductor(
                "conductor-1", "c001", LocalDate.of(2026, 9, 25),
                LocalTime.of(7, 0), LocalTime.of(7, 30));
        horario.setSentido("IDA");

        int notificados = service.notificarInicioRuta(horario, "BUS 1");
        assertEquals(1, notificados);
        assertEquals(List.of("pasajero@example.com"), correosEnviados);
        assertEquals(List.of("IDA"), sentidosNotificados);
    }
}
