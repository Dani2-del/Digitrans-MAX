package com.transcaribe.transcaribe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.MailSendException;

import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.TransaccionRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ServiceTranscaribeTest {

    @Mock
    private UsuarioRepository repositorioUsuarios;

    @Mock
    private TransaccionRepository repositorioTransacciones;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void calculaYRegistraElTotalSegunLaCantidadDePasajes() {
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                null);
        Usuario usuario = new Usuario("pasajero@example.com", "hash", "Pasajero");
        usuario.agregarTarjeta("1234567890", BigDecimal.ZERO);
        when(repositorioTransacciones.save(any(Transaccion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var transaccion = service.recargarPasajesEnTarjetaConRecibo(
                usuario, "1234567890", 10, "PSE", "pasajero.pse@example.com");

        assertTrue(transaccion.isPresent());
        assertEquals(39000.0, transaccion.get().getMonto());
        assertEquals(10, transaccion.get().getCantidadPasajes());
        assertEquals(0, new BigDecimal("39000").compareTo(usuario.getTarjetas().get(0).getSaldo()));
        verify(repositorioTransacciones).save(any(Transaccion.class));
    }

    @Test
    void registraElMontoEscritoEnLaRecargaNormal() {
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                null);
        Usuario usuario = new Usuario("pasajero@example.com", "hash", "Pasajero");
        usuario.agregarTarjeta("1234567890", BigDecimal.ZERO);
        when(repositorioTransacciones.save(any(Transaccion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var transaccion = service.recargarEnTarjetaConRecibo(
                usuario, "1234567890", 12500.0, "PSE", "pasajero.pse@example.com");

        assertTrue(transaccion.isPresent());
        assertEquals(12500.0, transaccion.get().getMonto());
        assertNull(transaccion.get().getCantidadPasajes());
        assertEquals(0, new BigDecimal("12500").compareTo(usuario.getTarjetas().get(0).getSaldo()));
        verify(repositorioTransacciones).save(any(Transaccion.class));
    }

    @Test
    void enviaElCodigoDeVerificacionAntesDeCompletarElRegistro() {
        RecordingEmailService emailService = new RecordingEmailService();
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                emailService);
        when(repositorioUsuarios.findByCorreo("pasajero@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");

        assertTrue(service.registrar(
                "Pasajero", "pasajero@example.com", "password", "1234567890"));

        verify(repositorioUsuarios).save(any(Usuario.class));
        assertEquals("pasajero@example.com", emailService.destinatario);
        assertEquals("Pasajero", emailService.nombre);
        org.junit.jupiter.api.Assertions.assertTrue(emailService.codigo.matches("\\d{6}"));
    }

    @Test
    void permiteReenviarCodigoAUnaCuentaPendienteDeVerificacion() {
        RecordingEmailService emailService = new RecordingEmailService();
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                emailService);
        Usuario usuario = new Usuario("pasajero@example.com", "hash", "Pasajero");
        usuario.setId("usuario-1");
        usuario.setCodigoVerificacion("123456");
        when(repositorioUsuarios.findByCorreo("pasajero@example.com")).thenReturn(Optional.of(usuario));
        when(repositorioUsuarios.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(ServiceTranscaribe.ResultadoReenvioCodigo.ENVIADO,
                service.reenviarCodigoVerificacion("pasajero@example.com", "pasajero@example.com"));

        verify(repositorioUsuarios).save(usuario);
        assertEquals("pasajero@example.com", emailService.destinatario);
        assertEquals("Pasajero", emailService.nombre);
        org.junit.jupiter.api.Assertions.assertTrue(emailService.codigo.matches("\\d{6}"));
    }

    @Test
    void actualizaElCorreoDeLaCuentaPendienteYEnviaElCodigoAlNuevoCorreo() {
        RecordingEmailService emailService = new RecordingEmailService();
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                emailService);
        Usuario usuario = new Usuario("correo-incorrecto@example.com", "hash", "Pasajero");
        usuario.setId("usuario-1");
        when(repositorioUsuarios.findByCorreo("correo-incorrecto@example.com")).thenReturn(Optional.of(usuario));
        when(repositorioUsuarios.findByCorreo("correcto@example.com")).thenReturn(Optional.empty());
        when(repositorioUsuarios.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(ServiceTranscaribe.ResultadoReenvioCodigo.ENVIADO,
                service.reenviarCodigoVerificacion("correo-incorrecto@example.com", "correcto@example.com"));

        assertEquals("correcto@example.com", usuario.getCorreo());
        assertEquals("correcto@example.com", emailService.destinatario);
        verify(repositorioUsuarios).save(usuario);
    }

    @Test
    void noPermiteCambiarAlCorreoDeOtraCuenta() {
        RecordingEmailService emailService = new RecordingEmailService();
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                emailService);
        Usuario pendiente = new Usuario("pendiente@example.com", "hash", "Pasajero");
        pendiente.setId("usuario-1");
        Usuario existente = new Usuario("ocupado@example.com", "hash", "Otro");
        existente.setId("usuario-2");
        when(repositorioUsuarios.findByCorreo("pendiente@example.com")).thenReturn(Optional.of(pendiente));
        when(repositorioUsuarios.findByCorreo("ocupado@example.com")).thenReturn(Optional.of(existente));

        assertEquals(ServiceTranscaribe.ResultadoReenvioCodigo.CORREO_EN_USO,
                service.reenviarCodigoVerificacion("pendiente@example.com", "ocupado@example.com"));

        assertEquals("pendiente@example.com", pendiente.getCorreo());
        assertNull(emailService.destinatario);
    }

    @Test
    void noReenviaCodigoSiLaCuentaYaFueVerificada() {
        RecordingEmailService emailService = new RecordingEmailService();
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                emailService);
        Usuario usuario = new Usuario("pasajero@example.com", "hash", "Pasajero");
        usuario.setVerificado(true);
        when(repositorioUsuarios.findByCorreo("pasajero@example.com")).thenReturn(Optional.of(usuario));

        assertEquals(ServiceTranscaribe.ResultadoReenvioCodigo.CUENTA_NO_PENDIENTE,
                service.reenviarCodigoVerificacion("pasajero@example.com", "pasajero@example.com"));

        assertNull(emailService.destinatario);
    }

    @Test
    void propagaElFalloDeCorreoParaNoIndicarQueElRegistroFueCompletado() {
        RecordingEmailService emailService = new RecordingEmailService();
        emailService.fallarEnvio = true;
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                emailService);
        when(repositorioUsuarios.findByCorreo("pasajero@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");

        assertThrows(
                MailSendException.class,
                () -> service.registrar(
                        "Pasajero", "pasajero@example.com", "password", "1234567890"));
    }

    @Test
    void preparaCambiosSinGuardarYAlmacenaLaNuevaContrasenaHasheada() {
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                null);
        Usuario usuario = new Usuario("actual@example.com", "hash-anterior", "Nombre anterior");
        usuario.setId("usuario-1");
        when(repositorioUsuarios.findById("usuario-1")).thenReturn(Optional.of(usuario));
        when(repositorioUsuarios.findByCorreo("nuevo@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("nueva-clave")).thenReturn("hash-nuevo");

        ServiceTranscaribe.CambioCredencialesPendiente cambio =
                service.prepararCambioCredenciales(
                        "usuario-1", "Nombre nuevo", "nuevo@example.com", "nueva-clave");

        assertEquals("Nombre anterior", usuario.getNombre());
        assertEquals("actual@example.com", usuario.getCorreo());
        assertEquals("hash-anterior", usuario.getPasswordHash());
        assertEquals("hash-nuevo", cambio.passwordHash());
        org.mockito.Mockito.verify(repositorioUsuarios, org.mockito.Mockito.never())
                .save(any(Usuario.class));
    }

    @Test
    void aplicaLosCambiosPreparadosSoloCuandoSeConfirmaElCodigo() {
        RecordingEmailService emailService = new RecordingEmailService();
        ServiceTranscaribe service = new ServiceTranscaribe(
                repositorioUsuarios,
                new TransaccionService(repositorioTransacciones),
                passwordEncoder,
                emailService);
        Usuario usuario = new Usuario("actual@example.com", "hash-anterior", "Nombre anterior");
        usuario.setId("usuario-1");
        when(repositorioUsuarios.findById("usuario-1")).thenReturn(Optional.of(usuario));
        when(repositorioUsuarios.findByCorreo("nuevo@example.com")).thenReturn(Optional.empty());
        when(repositorioUsuarios.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ServiceTranscaribe.CambioCredencialesPendiente cambio =
                new ServiceTranscaribe.CambioCredencialesPendiente(
                        "usuario-1", "Nombre nuevo", "nuevo@example.com", "hash-nuevo");

        assertTrue(service.aplicarCambioCredenciales(cambio));

        assertEquals("Nombre nuevo", usuario.getNombre());
        assertEquals("nuevo@example.com", usuario.getCorreo());
        assertEquals("hash-nuevo", usuario.getPasswordHash());
        verify(repositorioUsuarios).save(usuario);
    }

    private static class RecordingEmailService extends EmailService {
        private String destinatario;
        private String nombre;
        private String codigo;
        private boolean fallarEnvio;

        private RecordingEmailService() {
            super(org.mockito.Mockito.mock(JavaMailSender.class));
        }

        @Override
        public void enviarCodigoVerificacion(String destinatario, String nombre, String codigo) {
            this.destinatario = destinatario;
            this.nombre = nombre;
            this.codigo = codigo;
            if (fallarEnvio) {
                throw new MailSendException("SMTP rechazó el mensaje");
            }
        }

        @Override
        public void enviarNotificacionLogin(String destinatario, String nombre) {
        }
    }
}
