package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.ChatMessage;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void permiteEnviarMensajeDeAdministrador() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        ChatController controller = new ChatController(mongoTemplate, usuarioRepository);
        Authentication authentication = authentication("admin@example.test");
        Usuario admin = new Usuario();
        admin.setNombre("Admin");
        admin.setRole(Usuario.ROLE_ADMIN);
        admin.setActivo(true);
        when(usuarioRepository.findByCorreo("admin@example.test")).thenReturn(Optional.of(admin));

        String result = controller.enviarMensaje("  Hola equipo  ", authentication,
                new RedirectAttributesModelMap());

        assertEquals("redirect:/chat", result);
        verify(mongoTemplate).save(any(ChatMessage.class));
    }

    @Test
    void impideQueUsuariosRegularesLeanElChat() {
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        ChatController controller = new ChatController(mock(MongoTemplate.class), usuarioRepository);
        Usuario usuario = new Usuario();
        usuario.setRole(Usuario.ROLE_USER);
        usuario.setActivo(true);
        when(usuarioRepository.findByCorreo("user@example.test")).thenReturn(Optional.of(usuario));

        assertThrows(ResponseStatusException.class,
                () -> controller.listarMensajes(authentication("user@example.test")));
    }

    @Test
    void noGuardaMensajesVaciosNiMayoresAlLimite() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        ChatController controller = new ChatController(mongoTemplate, usuarioRepository);
        Authentication authentication = authentication("driver@example.test");
        Usuario conductor = new Usuario();
        conductor.setRole(Usuario.ROLE_CONDUCTOR);
        conductor.setActivo(true);
        when(usuarioRepository.findByCorreo("driver@example.test")).thenReturn(Optional.of(conductor));
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        controller.enviarMensaje("   ", authentication, attributes);
        controller.enviarMensaje("a".repeat(1001), authentication, attributes);

        verify(mongoTemplate, never()).save(any(ChatMessage.class));
        assertEquals("El mensaje debe tener entre 1 y 1000 caracteres.", attributes.getFlashAttributes().get("errorChat"));
    }

    @Test
    void permiteCompartirImagenValidaSinTexto() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        ChatController controller = new ChatController(mongoTemplate, usuarioRepository);
        Usuario conductor = participante("driver-1", Usuario.ROLE_CONDUCTOR);
        when(usuarioRepository.findByCorreo("driver@example.test")).thenReturn(Optional.of(conductor));
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile imagen = new MockMultipartFile("imagen", "aviso.png", "image/png", png);

        ChatController.MensajeRespuesta respuesta = controller.enviarMensajeApi(
                "", imagen, authentication("driver@example.test"));

        assertTrue(respuesta.hasImage());
        assertTrue(respuesta.canEdit());
        var captor = org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
        verify(mongoTemplate).save(captor.capture());
        assertEquals("driver-1", captor.getValue().getAuthorId());
        assertArrayEquals(png, captor.getValue().getImage());
    }

    @Test
    void rechazaArchivosQueNoSeanImagenesValidas() {
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        ChatController controller = new ChatController(mock(MongoTemplate.class), usuarioRepository);
        when(usuarioRepository.findByCorreo("driver@example.test"))
                .thenReturn(Optional.of(participante("driver-1", Usuario.ROLE_CONDUCTOR)));
        MockMultipartFile imagen = new MockMultipartFile(
                "imagen", "falso.png", "image/png", "no es una imagen".getBytes());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.enviarMensajeApi("", imagen, authentication("driver@example.test")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void editaElMensajePropioDentroDeLosQuinceMinutos() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        ChatController controller = new ChatController(mongoTemplate, usuarioRepository);
        when(usuarioRepository.findByCorreo("driver@example.test"))
                .thenReturn(Optional.of(participante("driver-1", Usuario.ROLE_CONDUCTOR)));
        ChatMessage actualizado = new ChatMessage();
        actualizado.setId("message-1");
        actualizado.setAuthorId("driver-1");
        actualizado.setAuthorName("Conductor");
        actualizado.setAuthorRole(Usuario.ROLE_CONDUCTOR);
        actualizado.setText("Actualizado 🎉");
        actualizado.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        actualizado.setEditedAt(LocalDateTime.now());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ChatMessage.class))).thenReturn(actualizado);

        ChatController.MensajeRespuesta respuesta = controller.editarMensaje(
                "message-1", new ChatController.EditarMensajeSolicitud("Actualizado 🎉"),
                authentication("driver@example.test"));

        assertEquals("Actualizado 🎉", respuesta.text());
        assertTrue(respuesta.edited());
        assertTrue(respuesta.canEdit());
    }

    @Test
    void noPermiteEditarMensajesQueNoCoincidenConAutorYVentana() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        ChatController controller = new ChatController(mongoTemplate, usuarioRepository);
        when(usuarioRepository.findByCorreo("driver@example.test"))
                .thenReturn(Optional.of(participante("driver-1", Usuario.ROLE_CONDUCTOR)));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ChatMessage.class))).thenReturn(null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.editarMensaje("message-1",
                        new ChatController.EditarMensajeSolicitud("Cambio no autorizado"),
                        authentication("driver@example.test")));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void entregaLaImagenGuardadaEnElMensaje() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        ChatController controller = new ChatController(mongoTemplate, usuarioRepository);
        when(usuarioRepository.findByCorreo("driver@example.test"))
                .thenReturn(Optional.of(participante("driver-1", Usuario.ROLE_CONDUCTOR)));
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        ChatMessage mensaje = new ChatMessage();
        mensaje.setId("message-1");
        mensaje.setImage(png);
        mensaje.setImageContentType(MediaType.IMAGE_PNG_VALUE);
        mensaje.setHasImage(true);
        when(mongoTemplate.findById("message-1", ChatMessage.class)).thenReturn(mensaje);

        var respuesta = controller.obtenerImagen(
                "message-1", authentication("driver@example.test"));

        assertEquals(MediaType.IMAGE_PNG, respuesta.getHeaders().getContentType());
        assertArrayEquals(png, respuesta.getBody());
    }

    private Usuario participante(String id, String role) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Conductor");
        usuario.setRole(role);
        usuario.setActivo(true);
        return usuario;
    }

    private Authentication authentication(String email) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(email);
        return authentication;
    }
}
