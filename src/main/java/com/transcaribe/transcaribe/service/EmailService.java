package com.transcaribe.transcaribe.service;

import jakarta.mail.internet.MimeMessage; // Importante: usamos Jakarta para Spring Boot 3
import jakarta.mail.MessagingException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.util.HtmlUtils;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remitente;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void enviarAvisosAdministrativosEnSegundoPlano(String avisoId,
                                                           List<DestinatarioAviso> destinatarios,
                                                           String asunto, String mensajeAviso,
                                                           byte[] imagen, String tipoContenidoImagen) {
        int enviados = 0;
        int fallidos = 0;
        for (DestinatarioAviso destinatario : destinatarios) {
            try {
                enviarAvisoAdministrativo(destinatario.correo(), destinatario.nombre(),
                        asunto, mensajeAviso, imagen, tipoContenidoImagen);
                enviados++;
            } catch (IllegalStateException exception) {
                fallidos++;
                LOGGER.warn("No se pudo enviar el aviso administrativo {} al destinatario {}",
                        avisoId, destinatario.usuarioId(), exception);
            }
        }
        LOGGER.info("Envío de correos del aviso administrativo {} completado: {} enviados, {} fallidos.",
                avisoId, enviados, fallidos);
    }

    public void enviarAvisoAdministrativo(String destinatario, String nombre,
                                           String asunto, String mensajeAviso) {
        enviarAvisoAdministrativo(destinatario, nombre, asunto, mensajeAviso, null, null);
    }

    public void enviarAvisoAdministrativo(String destinatario, String nombre,
                                           String asunto, String mensajeAviso,
                                           byte[] imagen, String tipoContenidoImagen) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, imagen != null, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setFrom(remitente);

            String mensajeHtml = HtmlUtils.htmlEscape(mensajeAviso).replace("\n", "<br>");
            String contenidoHtml =
                    "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 24px; max-width: 620px;'>" +
                    "<h2 style='color: #b75c16;'>Transcaribe · Aviso importante</h2>" +
                    "<p>Hola <strong>" + HtmlUtils.htmlEscape(nombre) + "</strong>,</p>" +
                    (imagen == null ? "" : "<img src='cid:imagenAviso' alt='Imagen del aviso' style='display:block; max-width:100%; height:auto; margin: 0 0 20px; border-radius:8px;'>") +
                    "<div style='white-space: pre-wrap; line-height: 1.6;'>" + mensajeHtml + "</div>" +
                    "<p style='font-size: 12px; color: #777; margin-top: 24px;'>Mensaje enviado por la administración de Transcaribe.</p>" +
                    "</div>";
            helper.setText(contenidoHtml, true);
            if (imagen != null) {
                helper.addInline("imagenAviso", new ByteArrayResource(imagen), tipoContenidoImagen);
            }
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("No se pudo enviar el aviso al correo " + destinatario + ".", exception);
        }
    }

    public record DestinatarioAviso(String usuarioId, String correo, String nombre) {
    }

    @Async
    public void enviarNotificacionHorarioAsignado(String destinatario, String nombre, String ruta,
                                                   LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        enviarNotificacionHorario(destinatario, nombre, ruta, fecha, horaInicio, horaFin,
                "📅 Nueva ruta asignada - Transcaribe",
                "Se te ha asignado una nueva ruta.");
    }

    @Async
    public void enviarNotificacionHorarioActualizado(String destinatario, String nombre, String ruta,
                                                     LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        enviarNotificacionHorario(destinatario, nombre, ruta, fecha, horaInicio, horaFin,
                "✏️ Ruta actualizada - Transcaribe",
                "El administrador ha actualizado uno de tus horarios.");
    }

    public void enviarReporteConductor(List<String> destinatarios, String nombreConductor,
                                       String correoConductor, String asunto, String detalle) {
        if (destinatarios == null || destinatarios.isEmpty()) {
            throw new IllegalStateException("No hay administradores activos para recibir el reporte.");
        }
        if (asunto == null || asunto.isBlank() || detalle == null || detalle.isBlank()) {
            throw new IllegalArgumentException("El asunto y el detalle del reporte son obligatorios.");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(destinatarios.toArray(new String[0]));
            helper.setSubject("[Reporte de conductor] " + asunto.trim());
            helper.setFrom(remitente);

            String contenidoHtml =
                    "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 620px;'>" +
                    "<h2 style='color: #0f3948;'>Reporte de conductor - Transcaribe</h2>" +
                    "<p><strong>Conductor:</strong> " + HtmlUtils.htmlEscape(nombreConductor) + "</p>" +
                    "<p><strong>Correo:</strong> " + HtmlUtils.htmlEscape(correoConductor) + "</p>" +
                    "<p><strong>Asunto:</strong> " + HtmlUtils.htmlEscape(asunto.trim()) + "</p>" +
                    "<div style='background-color: #f8f9fa; padding: 15px; border-radius: 6px; white-space: pre-wrap;'>" +
                    HtmlUtils.htmlEscape(detalle.trim()) +
                    "</div>" +
                    "<p style='font-size: 12px; color: #888; margin-top: 20px;'>Reporte enviado desde el panel del conductor.</p>" +
                    "</div>";

            helper.setText(contenidoHtml, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo enviar el reporte a los administradores.", e);
        }
    }

    private void enviarNotificacionHorario(String destinatario, String nombre, String ruta,
                                           LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                           String asunto, String mensaje) {
        if (destinatario == null || destinatario.isBlank()) {
            System.err.println("No se envió la notificación de horario: el conductor no tiene correo.");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setFrom(remitente);

            String contenidoHtml =
                    "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 560px;'>" +
                    "<h2 style='color: #2a9d8f; text-align: center;'>🚍 Transcaribe</h2>" +
                    "<p>Hola <strong>" + nombre + "</strong>,</p>" +
                    "<p>" + mensaje + "</p>" +
                    "<div style='background-color: #f1f8f9; padding: 15px; border-radius: 6px; border-left: 5px solid #2a9d8f;'>" +
                    "<p style='margin: 0 0 8px 0;'><strong>Detalles del horario:</strong></p>" +
                    "<p style='margin: 4px 0;'>🚌 <strong>Ruta:</strong> " + ruta + "</p>" +
                    "<p style='margin: 4px 0;'>📆 <strong>Fecha:</strong> " + fecha + "</p>" +
                    "<p style='margin: 4px 0;'>🕐 <strong>Horario:</strong> " + horaInicio + " - " + horaFin + "</p>" +
                    "</div>" +
                    "<p style='font-size: 12px; color: #888; margin-top: 20px;'>Este es un correo automático de Transcaribe.</p>" +
                    "</div>";

            helper.setText(contenidoHtml, true);
            mailSender.send(message);
            System.out.println("Notificación de horario enviada a: " + destinatario);
        } catch (Exception e) {
            System.err.println("Error al enviar notificación de horario a " + destinatario + ": " + e.getMessage());
        }
    }

    @Async
    public void enviarCorreoBienvenida(String destinatario, String nombre, String numeroTarjeta) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(destinatario);
            helper.setSubject("🚍 ¡Bienvenido a Transcaribe, " + nombre + "!");
            helper.setFrom(remitente);

            String contenidoHtml = 
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 600px;'>" +
                    "<h2 style='color: #e63946; text-align: center;'>🚍 Transcaribe</h2>" +
                    "<p>Hola <strong>" + nombre + "</strong>,</p>" +
                    "<p>Tu cuenta ha sido activada con éxito en nuestro sistema. Ya puedes empezar a moverte por Cartagena.</p>" +
                    "<div style='background-color: #f8f9fa; padding: 15px; border-radius: 5px; border-left: 5px solid #e63946;'>" +
                        "<p style='margin: 0;'><strong>Detalles de tu cuenta:</strong></p>" +
                        "<ul style='list-style: none; padding: 0;'>" +
                            "<li>💳 <strong>Tarjeta vinculada:</strong> **** **** " + numeroTarjeta.substring(numeroTarjeta.length() - 4) + "</li>" +
                            "<li>💰 <strong>Saldo inicial:</strong> $0.00</li>" +
                        "</ul>" +
                    "</div>" +
                    "<p style='margin-top: 20px;'>Recuerda que puedes recargar tu saldo desde el menú de usuario.</p>" +
                    "<hr style='border: 0; border-top: 1px solid #eee;'>" +
                    "<p style='font-size: 12px; color: #888; text-align: center;'>Cartagena se mueve contigo. <br> Este es un correo automático, por favor no lo respondas.</p>" +
                "</div>";

            helper.setText(contenidoHtml, true); 

            mailSender.send(message);
            System.out.println("Correo HTML enviado a: " + destinatario);
        } catch (Exception e) {
            System.err.println("Error al enviar correo HTML: " + e.getMessage());
        }
    }

    public void enviarCodigoVerificacion(String destinatario, String nombre, String codigo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(destinatario);
            helper.setSubject("Código de verificación de Transcaribe");
            helper.setFrom(remitente);

            String contenidoHtml =
                    "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px; text-align: center;'>" +
                    "<h2 style='color: #e63946;'>Verificación de Cuenta</h2>" +
                    "<p>Hola <strong>" + HtmlUtils.htmlEscape(nombre) + "</strong>, usa el siguiente código para completar tu registro:</p>" +
                    "<div style='background-color: #f4f4f4; border: 1px dashed #e63946; padding: 20px; margin: 20px 0;'>" +
                    "<span style='font-size: 32px; font-weight: bold; letter-spacing: 5px; color: #333;'>" + HtmlUtils.htmlEscape(codigo) + "</span>" +
                    "</div>" +
                    "<p style='font-size: 12px; color: #888;'>Este código es privado. Si no solicitaste este registro, ignora este correo.</p>" +
                    "</div>";

            helper.setText(contenidoHtml, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new MailSendException("No se pudo enviar el código de verificación.", e);
        }
    }

    public void enviarCodigoVerificacionCambioCredenciales(String destinatario, String nombre, String codigo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject("Confirma los cambios de tu cuenta Transcaribe");
            helper.setFrom(remitente);

            String contenidoHtml =
                    "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px; text-align: center;'>" +
                    "<h2 style='color: #B75C16;'>Confirmación de cambios</h2>" +
                    "<p>Hola <strong>" + HtmlUtils.htmlEscape(nombre) + "</strong>, usa este código para confirmar los cambios solicitados en tu perfil:</p>" +
                    "<div style='background-color: #f4f4f4; border: 1px dashed #FF5600; padding: 20px; margin: 20px 0;'>" +
                    "<span style='font-size: 32px; font-weight: bold; letter-spacing: 5px; color: #333;'>" + HtmlUtils.htmlEscape(codigo) + "</span>" +
                    "</div>" +
                    "<p style='font-size: 12px; color: #888;'>El código vence en 10 minutos. Si no solicitaste estos cambios, ignora este correo.</p>" +
                    "</div>";

            helper.setText(contenidoHtml, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new MailSendException("No se pudo enviar el código de confirmación de cambios.", e);
        }
    }
        
@Async
public void enviarNotificacionLogin(String destinatario, String nombre) {
    try {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(destinatario);
        helper.setSubject("Nuevo inicio de sesión detectado - Transcaribe");
        helper.setFrom(remitente);

        String contenidoHtml = 
            "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px;'>" +
                "<h2 style='color: #2a9d8f;'>¡Hola " + nombre + "!</h2>" +
                "<p>Te informamos que se ha iniciado sesión en tu cuenta de Transcaribe.</p>" +
                "<p style='font-size: 13px; color: #555;'>Si no fuiste tú, por favor cambia tu contraseña de inmediato.</p>" +
                "<p style='text-align: center; color: #888;'>Cartagena, " + java.time.LocalDate.now() + "</p>" +
            "</div>";

        helper.setText(contenidoHtml, true);
        mailSender.send(message);
    } catch (Exception e) {
        System.err.println("Error notificación login: " + e.getMessage());
    }
}

@Async
public void enviarNotificacionRecarga(String destinatario, String nombre, double monto, String saldoActual) {
    try {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(destinatario);
        helper.setSubject("✅ Recarga exitosa - Transcaribe");
        helper.setFrom(remitente);

        String contenidoHtml = 
            "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px;'>" +
                "<h2 style='color: #2b9348; text-align: center;'>¡Recarga Exitosa!</h2>" +
                "<p>Hola <strong>" + nombre + "</strong>, has recargado tu cuenta correctamente.</p>" +
                "<div style='background-color: #f1f8e9; padding: 15px; border-radius: 5px; border-left: 5px solid #2b9348;'>" +
                    "💰 <strong>Monto recargado:</strong> $" + monto + "<br>" +
                    "💳 <strong>Nuevo saldo:</strong> $" + saldoActual + 
                "</div>" +
            "</div>";

        helper.setText(contenidoHtml, true);
        mailSender.send(message);
    } catch (Exception e) {
        System.err.println("Error notificación recarga: " + e.getMessage());
    }
}

@Async
public void enviarNotificacionGasto(String destinatario, String nombre, double monto, String saldoRestante) {
    try {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(destinatario);
        helper.setSubject("Ticket de Viaje - Transcaribe");
        helper.setFrom(remitente);

        String contenidoHtml = 
            "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px;'>" +
                "<h3 style='color: #e63946;'>Uso de saldo</h3>" +
                "<p>Se ha descontado un valor de tu saldo por uso del servicio.</p>" +
                "<table style='width: 100%; border-collapse: collapse;'>" +
                    "<tr><td style='padding: 5px;'>Monto descontado:</td><td style='text-align: right;'><strong>-$" + monto + "</strong></td></tr>" +
                    "<tr><td style='padding: 5px;'>Saldo disponible:</td><td style='text-align: right;'>$" + saldoRestante + "</td></tr>" +
                "</table>" +
            "</div>";

        helper.setText(contenidoHtml, true);
        mailSender.send(message);
    } catch (Exception e) {
        System.err.println("Error notificación gasto: " + e.getMessage());
    }
}

 @Async
    public void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // Usamos false en multipart porque es un HTML simple sin archivos adjuntos
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setTo(destinatario);
            helper.setSubject(codigo + " es tu código para restablecer contraseña - Transcaribe");
            helper.setFrom(remitente);

            String contenidoHtml = 
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px; text-align: center;'>" +
                    "<h2 style='color: #e63946;'>Restablecer Contraseña</h2>" +
                    "<p>Hola <strong>" + nombre + "</strong>,</p>" +
                    "<p>Has solicitado restablecer tu contraseña. Usa el siguiente código de seguridad:</p>" +
                    "<div style='background-color: #fff3f3; border: 1px solid #e63946; padding: 20px; margin: 20px 0; border-radius: 8px;'>" +
                        "<span style='font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #e63946;'>" + codigo + "</span>" +
                    "</div>" +
                    "<p style='font-size: 13px; color: #555;'>Este código vencerá en unos minutos por seguridad.</p>" +
                    "<hr style='border: 0; border-top: 1px solid #eee; margin: 20px 0;'>" +
                    "<p style='font-size: 12px; color: #888;'>Si no solicitaste este cambio, puedes ignorar este mensaje.</p>" +
                "</div>";

            helper.setText(contenidoHtml, true);
            mailSender.send(message);
            System.out.println(" Correo enviado con éxito a: " + destinatario);
        } catch (Exception e) {
            System.err.println(" Error crítico al enviar correo: " + e.getMessage());
            e.printStackTrace(); 
        }
    }

    @Async
    public void enviarNotificacionRutaIniciada(
            String destinatario, String nombre, String ruta, String sentido,
            String placaBus, String horaProgramada) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(destinatario);
            helper.setSubject("🚌 Tu ruta favorita " + ruta + " acaba de salir - Transcaribe");
            helper.setFrom(remitente);

            String contenidoHtml =
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px;'>" +
                    "<h2 style='color: #ff8c00; text-align: center;'>🚌 ¡Tu ruta favorita va en camino!</h2>" +
                    "<p>Hola <strong>" + nombre + "</strong>,</p>" +
                    "<p>El bus de la ruta <strong>" + ruta + "</strong> en sentido <strong>"
                        + sentido + "</strong> (placa " + placaBus + ") acaba de iniciar su recorrido.</p>" +
                    "<p>Hora de salida programada: <strong>" + horaProgramada + "</strong>.</p>" +
                    "<p style='font-size: 13px; color: #555;'>Te avisamos porque tienes esta ruta guardada en tus favoritas.</p>" +
                "</div>";

            helper.setText(contenidoHtml, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Error notificación ruta iniciada: " + e.getMessage());
        }
    }
    @Async
    public void enviarAlertaSaldoBajo(String destinatario, String nombre, String numeroTarjeta, String saldoActual, String limite) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(destinatario);
            helper.setSubject("⚠️ Alerta de Saldo Bajo - Transcaribe");
            helper.setFrom(remitente);

            String tarjetaOculta = (numeroTarjeta != null && numeroTarjeta.length() >= 4)
                    ? "**** **** " + numeroTarjeta.substring(numeroTarjeta.length() - 4)
                    : "****";

            String contenidoHtml =
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px;'>" +
                    "<h2 style='color: #d97706; text-align: center;'>⚠️ Alerta de Saldo Bajo</h2>" +
                    "<p>Hola <strong>" + nombre + "</strong>,</p>" +
                    "<p>Tu tarjeta de Transcaribe ha alcanzado o superado el límite de saldo mínimo configurado.</p>" +
                    "<div style='background-color: #fffbeeb0; padding: 15px; border-radius: 5px; border-left: 5px solid #d97706; margin: 15px 0;'>" +
                        "💳 <strong>Tarjeta:</strong> " + tarjetaOculta + "<br>" +
                        "💰 <strong>Saldo actual:</strong> $" + saldoActual + " COP<br>" +
                        "⚙️ <strong>Límite configurado:</strong> $" + limite + " COP" +
                    "</div>" +
                    "<p style='font-size: 13px; color: #555;'>Te recomendamos realizar una recarga pronto para continuar viajando sin inconvenientes.</p>" +
                "</div>";

            helper.setText(contenidoHtml, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Error al enviar alerta de saldo bajo: " + e.getMessage());
        }
    }
}
