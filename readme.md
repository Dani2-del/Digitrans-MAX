# Digitrans - Recargas en línea de Transcaribe

Aplicación web académica cuyo objetivo principal es permitir a los usuarios gestionar y realizar recargas en línea para sus tarjetas Transcaribe. También reúne herramientas complementarias de consulta de rutas y apoyo administrativo y operativo para el sistema de transporte de Cartagena de Indias.

## Información académica

- **Carrera:** Tecnología en Desarrollo de Software
- **Semestre:** VI
- **Periodo:** 2026-2

### Integrantes

1. Andres Felipe Pérez Ferrer
2. Elías Daniel Blanco Meza
3. Andrés Sebastián Garzón Ramírez
4. Juan Sebastián Correa Ramos
5. Danier Alejandro Ospino Murillo

## Funcionalidades

### Pasajeros

- Registro, inicio de sesión, verificación de cuenta y recuperación de contraseña.
- Gestión de tarjetas Transcaribe y recarga de saldo en línea.
- Consulta del saldo y del historial de recargas y transacciones.
- Consulta del mapa, las rutas y sus recorridos.
- Gestión de rutas favoritas y notificaciones relacionadas.
- Recepción de avisos administrativos.

### Conductores

- Panel con información de rutas y horarios asignados.
- Inicio y finalización de recorridos y reporte de novedades.
- Acceso al chat operativo compartido con los administradores.

### Administración

- Gestión de usuarios, buses, rutas y horarios de conductores.
- Consulta de transacciones y generación de reportes.
- Publicación de avisos con destinatarios por tipo de cuenta o selección individual.
- Chat operativo con envío de imágenes, emojis y edición de mensajes propios durante los primeros 15 minutos.

## Tecnologías

- Java 21 y Spring Boot 3.5.
- Spring MVC y Thymeleaf para la aplicación web.
- Spring Security, JWT y OAuth 2.0 para autenticación y control de acceso.
- MongoDB para persistencia.
- Spring Mail para notificaciones por correo.
- Apache POI y Apache PDFBox para generación de reportes y documentos.
- HTML, CSS y JavaScript para la interfaz.

## Requisitos

- JDK 21.
- MongoDB accesible desde el entorno donde se ejecuta la aplicación.
- Credenciales de correo para las funciones de notificación.
- Maven Wrapper incluido en el repositorio.

## Configuración y ejecución local

Configura las propiedades sensibles mediante variables de entorno y evita guardar credenciales reales en el repositorio. Como mínimo, la aplicación requiere acceso a MongoDB y la cuenta de correo configurada para Spring Mail. Para habilitar el inicio de sesión con Google, configura también las credenciales OAuth 2.0 de Google.

En PowerShell, asigna los valores correspondientes en el entorno local y ejecuta:

```powershell
.\mvnw.cmd spring-boot:run