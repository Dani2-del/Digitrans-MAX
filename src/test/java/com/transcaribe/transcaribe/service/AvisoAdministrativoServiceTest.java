package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.AvisoAdministrativo;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.AvisoAdministrativoRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvisoAdministrativoServiceTest {

    @Mock
    private AvisoAdministrativoRepository avisoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private AvisoAdministrativoService service;
    private List<String> correosEnviados;

    @BeforeEach
    void setUp() {
        correosEnviados = new java.util.ArrayList<>();
        EmailService emailService = new EmailService(null) {
            @Override
            public void enviarAvisoAdministrativo(String destinatario, String nombre,
                                                   String asunto, String mensaje) {
                correosEnviados.add(destinatario);
            }

            @Override
            public void enviarAvisoAdministrativo(String destinatario, String nombre,
                                                   String asunto, String mensaje,
                                                   byte[] imagen, String tipoContenidoImagen) {
                correosEnviados.add(destinatario);
            }

            @Override
            public void enviarAvisosAdministrativosEnSegundoPlano(
                    String avisoId, List<DestinatarioAviso> destinatarios, String asunto,
                    String mensajeAviso, byte[] imagen, String tipoContenidoImagen) {
                destinatarios.forEach(destinatario -> correosEnviados.add(destinatario.correo()));
            }
        };
        service = new AvisoAdministrativoService(avisoRepository, usuarioRepository, emailService);
    }

    @Test
    void enviaAvisoATodosLosRolesElegiblesYGuardaListaDeDestinatarios() {
        Usuario pasajero = usuario("user-1", "pasajero@example.com", Usuario.ROLE_USER);
        Usuario administrador = usuario("admin-1", "admin@example.com", Usuario.ROLE_ADMIN);
        Usuario conductor = usuario("driver-1", "conductor@example.com", Usuario.ROLE_CONDUCTOR);
        Usuario cuentaSinVerificar = usuario("pending-1", "pendiente@example.com", Usuario.ROLE_ADMIN);
        cuentaSinVerificar.setVerificado(false);
        when(usuarioRepository.findAll()).thenReturn(
                List.of(pasajero, administrador, conductor, cuentaSinVerificar));
        when(avisoRepository.save(any(AvisoAdministrativo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AvisoAdministrativoService.ResultadoEnvio resultado =
                service.crearAviso("Cambio de servicio", "Información importante", true,
                        List.of(Usuario.ROLE_USER, Usuario.ROLE_ADMIN, Usuario.ROLE_CONDUCTOR), List.of());

        assertEquals(3, resultado.totalDestinatarios());
        assertEquals(3, resultado.correosProgramados());
        assertEquals(List.of("admin@example.com", "conductor@example.com", "pasajero@example.com"),
                correosEnviados);
        ArgumentCaptor<AvisoAdministrativo> avisoCaptor = ArgumentCaptor.forClass(AvisoAdministrativo.class);
        verify(avisoRepository).save(avisoCaptor.capture());
        assertEquals(List.of("admin-1", "driver-1", "user-1"), avisoCaptor.getValue().getDestinatarios());
    }

    @Test
    void rechazaEnvioSinDestinatariosEspecificos() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crearAviso("Asunto", "Mensaje", false,
                        List.of(Usuario.ROLE_USER), List.of()));
    }

    @Test
    void enviaAvisoSoloALosDestinatariosSeleccionados() {
        Usuario primero = usuario("user-1", "uno@example.com", Usuario.ROLE_USER);
        Usuario segundo = usuario("user-2", "dos@example.com", Usuario.ROLE_CONDUCTOR);
        when(usuarioRepository.findAllById(any()))
                .thenReturn(List.of(primero, segundo));
        when(avisoRepository.save(any(AvisoAdministrativo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AvisoAdministrativoService.ResultadoEnvio resultado =
                service.crearAviso("Asunto", "Mensaje", false,
                        List.of(Usuario.ROLE_USER, Usuario.ROLE_CONDUCTOR), List.of("user-1", "user-2"));

        assertEquals(2, resultado.totalDestinatarios());
        assertEquals(List.of("uno@example.com", "dos@example.com"), correosEnviados);
    }

    @Test
    void guardaImagenValidaYPermiteQueElDestinatarioLaConsulte() {
        Usuario destinatario = usuario("user-1", "uno@example.com", Usuario.ROLE_USER);
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile imagen = new MockMultipartFile(
                "imagen", "aviso.png", "image/png", png);
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(destinatario));
        when(avisoRepository.save(any(AvisoAdministrativo.class)))
                .thenAnswer(invocation -> {
                    AvisoAdministrativo guardado = invocation.getArgument(0);
                    guardado.setId("aviso-1");
                    return guardado;
                });

        service.crearAviso("Asunto", "Mensaje", false,
                List.of(Usuario.ROLE_USER), List.of("user-1"), imagen);

        ArgumentCaptor<AvisoAdministrativo> avisoCaptor = ArgumentCaptor.forClass(AvisoAdministrativo.class);
        verify(avisoRepository).save(avisoCaptor.capture());
        AvisoAdministrativo aviso = avisoCaptor.getValue();
        assertArrayEquals(png, aviso.getImagen());
        assertEquals("image/png", aviso.getImagenTipoContenido());

        when(avisoRepository.findById(aviso.getId())).thenReturn(Optional.of(aviso));
        assertArrayEquals(png, service.obtenerImagenParaUsuario(aviso.getId(), "user-1", false)
                .contenido());
    }

    @Test
    void rechazaArchivoQueNoSeaImagenValida() {
        MockMultipartFile imagen = new MockMultipartFile(
                "imagen", "documento.png", "image/png", "no es una imagen".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> service.crearAviso("Asunto", "Mensaje", false,
                        List.of(Usuario.ROLE_USER), List.of("user-1"), imagen));
    }

    @Test
    void noPermiteQueUnNoDestinatarioDescargueLaImagen() {
        AvisoAdministrativo aviso = new AvisoAdministrativo("Asunto", "Mensaje", List.of("user-1"));
        aviso.setImagen(new byte[] {1});
        aviso.setImagenTipoContenido("image/png");
        when(avisoRepository.findById("aviso-1")).thenReturn(Optional.of(aviso));

        assertThrows(SecurityException.class,
                () -> service.obtenerImagenParaUsuario("aviso-1", "user-2", false));
    }

    @Test
    void enviaPorTipoSoloAAdministradoresYConductores() {
        Usuario pasajero = usuario("user-1", "pasajero@example.com", Usuario.ROLE_USER);
        Usuario administrador = usuario("admin-1", "admin@example.com", Usuario.ROLE_ADMIN);
        Usuario conductor = usuario("driver-1", "conductor@example.com", Usuario.ROLE_CONDUCTOR);
        when(usuarioRepository.findAll()).thenReturn(List.of(pasajero, administrador, conductor));
        when(avisoRepository.save(any(AvisoAdministrativo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AvisoAdministrativoService.ResultadoEnvio resultado =
                service.crearAviso("Asunto", "Mensaje", true,
                        List.of(Usuario.ROLE_ADMIN, Usuario.ROLE_CONDUCTOR), List.of());

        assertEquals(2, resultado.totalDestinatarios());
        assertEquals(List.of("admin@example.com", "conductor@example.com"), correosEnviados);
    }

    @Test
    void rechazaDestinatariosIndividualesFueraDeLosRolesSeleccionados() {
        Usuario pasajero = usuario("user-1", "pasajero@example.com", Usuario.ROLE_USER);
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(pasajero));

        assertThrows(IllegalArgumentException.class,
                () -> service.crearAviso("Asunto", "Mensaje", false,
                        List.of(Usuario.ROLE_ADMIN), List.of("user-1")));
    }

    @Test
    void obtieneSoloLosDosAvisosMasRecientesParaElPanel() {
        when(avisoRepository.findTop2ByOrderByFechaCreacionDesc()).thenReturn(List.of());

        assertEquals(List.of(), service.obtenerAvisosRecientes());
        verify(avisoRepository).findTop2ByOrderByFechaCreacionDesc();
    }

    @Test
    void obtieneTodosLosAvisosParaElHistorial() {
        when(avisoRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of());

        assertEquals(List.of(), service.obtenerHistorialAvisos());
        verify(avisoRepository).findAllByOrderByFechaCreacionDesc();
    }

    @Test
    void alCerrarAvisoGuardaElUsuarioParaNoMostrarloDeNuevo() {
        AvisoAdministrativo aviso = new AvisoAdministrativo(
                "Asunto", "Mensaje", List.of("user-1", "user-2"));
        when(avisoRepository.findById("aviso-1")).thenReturn(Optional.of(aviso));

        service.descartarParaUsuario("aviso-1", "user-1");

        assertEquals(List.of("user-1"), aviso.getDescartadaPor());
        verify(avisoRepository).save(aviso);
    }

    @Test
    void noPermiteCerrarAvisosDeOtroUsuario() {
        AvisoAdministrativo aviso = new AvisoAdministrativo("Asunto", "Mensaje", List.of("user-1"));
        when(avisoRepository.findById("aviso-1")).thenReturn(Optional.of(aviso));

        assertThrows(SecurityException.class,
                () -> service.descartarParaUsuario("aviso-1", "user-2"));
    }

    private Usuario usuario(String id, String correo, String rol) {
        Usuario usuario = new Usuario(correo, "hash", correo);
        usuario.setId(id);
        usuario.setRole(rol);
        usuario.setVerificado(true);
        return usuario;
    }
}
