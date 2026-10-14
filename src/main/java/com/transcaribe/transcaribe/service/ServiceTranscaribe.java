package com.transcaribe.transcaribe.service;

import java.math.BigDecimal;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.transcaribe.transcaribe.Model.Tarjeta;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.Util.TarifaTranscaribe;

/**
 * Servicio principal del sistema Transcaribe para gestión de usuarios,
 * autenticación OTP, tarjetas, transacciones y alertas por correo.
 */
@Service
public class ServiceTranscaribe {

    private static final SecureRandom GENERADOR_CODIGOS = new SecureRandom();

    private final UsuarioRepository repositorioUsuarios;
    private final TransaccionService servicioTransacciones;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public ServiceTranscaribe(UsuarioRepository repositorioUsuarios,
                               TransaccionService servicioTransacciones,
                               PasswordEncoder passwordEncoder,
                               EmailService emailService) {
        this.repositorioUsuarios = repositorioUsuarios;
        this.servicioTransacciones = servicioTransacciones;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Registra un nuevo usuario en la plataforma generando un código OTP.
     */
    public boolean registrar(String nombre, String correo, String contrasena, String numeroTarjeta) {
        if (repositorioUsuarios.findByCorreo(correo).isPresent()) {
            return false;
        }
        String numeroLimpio = numeroTarjeta.replaceAll("\\D", "");
        if (numeroLimpio.length() != 10) {
            return false;
        }
        
        String codigoOtp = String.format("%06d", GENERADOR_CODIGOS.nextInt(1000000));

        Usuario nuevoUsuario = new Usuario(correo, passwordEncoder.encode(contrasena), nombre);
        
        nuevoUsuario.agregarTarjeta(numeroLimpio, BigDecimal.ZERO);
        nuevoUsuario.setCodigoVerificacion(codigoOtp);
        nuevoUsuario.setVerificado(false); 

        repositorioUsuarios.save(nuevoUsuario);
        emailService.enviarCodigoVerificacion(correo, nombre, codigoOtp);
        return true;
    }

    public ResultadoReenvioCodigo reenviarCodigoVerificacion(String correoActual, String correoNuevo) {
        String nuevoCorreoNormalizado = correoNuevo == null ? "" : correoNuevo.trim();
        if (!nuevoCorreoNormalizado.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return ResultadoReenvioCodigo.CORREO_INVALIDO;
        }

        Optional<Usuario> usuarioPendiente = repositorioUsuarios.findByCorreo(correoActual)
                .filter(usuario -> !usuario.isVerificado());
        if (usuarioPendiente.isEmpty()) {
            return ResultadoReenvioCodigo.CUENTA_NO_PENDIENTE;
        }

        Usuario usuario = usuarioPendiente.get();
        Optional<Usuario> usuarioConCorreo = repositorioUsuarios.findByCorreo(nuevoCorreoNormalizado);
        if (usuarioConCorreo.isPresent()
                && !Objects.equals(usuario.getId(), usuarioConCorreo.get().getId())) {
            return ResultadoReenvioCodigo.CORREO_EN_USO;
        }

        String codigoOtp = String.format("%06d", GENERADOR_CODIGOS.nextInt(1000000));
        usuario.setCorreo(nuevoCorreoNormalizado);
        usuario.setCodigoVerificacion(codigoOtp);
        repositorioUsuarios.save(usuario);
        emailService.enviarCodigoVerificacion(nuevoCorreoNormalizado, usuario.getNombre(), codigoOtp);
        return ResultadoReenvioCodigo.ENVIADO;
    }

    public enum ResultadoReenvioCodigo {
        ENVIADO,
        CUENTA_NO_PENDIENTE,
        CORREO_EN_USO,
        CORREO_INVALIDO
    }

    /**
     * Valida el código de verificación ingresado por el usuario.
     */
    public boolean verificarCodigo(String correo, String codigoIntroducido) {
        Optional<Usuario> opt = repositorioUsuarios.findByCorreo(correo);

        if (opt.isPresent()) {
            Usuario u = opt.get();
            if (u.getCodigoVerificacion() != null && u.getCodigoVerificacion().equals(codigoIntroducido)) {
                u.setVerificado(true);
                u.setCodigoVerificacion(null); 
                repositorioUsuarios.save(u);

                String numTarjeta = u.getTarjetas().isEmpty() ? "N/A" : u.getTarjetas().get(0).getNumeroTarjeta();
                try {
                    emailService.enviarCorreoBienvenida(u.getCorreo(), u.getNombre(), numTarjeta);
                } catch (Exception e) {
                    System.err.println("Error al enviar correo bienvenida: " + e.getMessage());
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Recarga saldo directo a la primera tarjeta asociada del usuario.
     */
    public void recargar(Usuario usuario, double monto) {
        if (usuario != null && usuario.isActivo() && monto > 0 && !usuario.getTarjetas().isEmpty()) {
            Tarjeta t = usuario.getTarjetas().get(0);
            t.setSaldo(t.getSaldo().add(BigDecimal.valueOf(monto)));
            
            repositorioUsuarios.save(usuario);
            servicioTransacciones.registrarTransaccion(usuario, "Recarga Directa", monto);
            emailService.enviarNotificacionRecarga(usuario.getCorreo(), usuario.getNombre(), monto, t.getSaldo().toString());
        }
    }

    /**
     * Descuenta un monto de la primera tarjeta del usuario.
     */


    /**
     * Realiza una recarga de saldo filtrando por el número de tarjeta específico.
     */
    public boolean recargarEnTarjeta(Usuario usuario, String numeroTarjeta, double monto) {
        return recargarEnTarjetaConRecibo(usuario, numeroTarjeta, monto).isPresent();
    }

    public Optional<Transaccion> recargarEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, double monto) {
        return recargarEnTarjetaConRecibo(usuario, numeroTarjeta, monto, null, null);
    }

    public Optional<Transaccion> recargarEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, double monto, String metodoPago, String cuentaPse) {
        return recargarEnTarjetaConRecibo(usuario, numeroTarjeta, monto, metodoPago, cuentaPse, null);
    }

    public Optional<Transaccion> recargarPasajesEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, int cantidadPasajes, String metodoPago, String cuentaPse) {
        if (cantidadPasajes <= 0) {
            return Optional.empty();
        }
        long monto = Math.multiplyExact((long) cantidadPasajes, TarifaTranscaribe.VALOR_PASAJE_COP);
        return recargarEnTarjetaConRecibo(
                usuario, numeroTarjeta, (double) monto, metodoPago, cuentaPse, cantidadPasajes);
    }

    private Optional<Transaccion> recargarEnTarjetaConRecibo(
            Usuario usuario, String numeroTarjeta, double monto, String metodoPago,
            String cuentaPse, Integer cantidadPasajes) {
        if (usuario == null || !usuario.isActivo() || monto <= 0) return Optional.empty();

        return usuario.getTarjetas().stream()
                .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta))
                .findFirst()
                .map(t -> {
                    t.setSaldo(t.getSaldo().add(BigDecimal.valueOf(monto)));
                    repositorioUsuarios.save(usuario);
                    return servicioTransacciones.registrarTransaccion(
                            usuario,
                            "Recarga Tarjeta: " + ocultarNumeroTarjeta(numeroTarjeta),
                            monto,
                            metodoPago,
                            cuentaPse,
                            ocultarNumeroTarjeta(numeroTarjeta),
                            cantidadPasajes);
                });
    }

    /**
     * Obtiene la primera tarjeta de la lista del usuario.
     */
    public Tarjeta obtenerTarjetaDeUsuario(Usuario usuario) {
        if (usuario == null || usuario.getTarjetas().isEmpty()) return null;
        return usuario.getTarjetas().get(0);
    }

    /**
     * Cobro de pasaje administrativo en la primera tarjeta activa del usuario.
     */



    /**
     * Establece la configuración de límite de saldo mínimo en una tarjeta especificada
     * y comprueba de inmediato si el saldo actual ya se encuentra por debajo del umbral.
     */
    public boolean establecerLimiteSaldo(Usuario usuario, String numeroTarjeta, BigDecimal limiteSaldo) {
        if (usuario == null || numeroTarjeta == null || limiteSaldo == null) return false;

        return usuario.getTarjetas().stream()
                .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta))
                .findFirst()
                .map(t -> {
                    t.setLimiteSaldo(limiteSaldo);
                    repositorioUsuarios.save(usuario);
                    
                    // Comprobación y envío inmediato si el saldo actual ya es menor o igual al nuevo límite
                    verificarYNotificarLimiteSaldo(usuario, t);
                    
                    return true;
                }).orElse(false);
    }

    /**
     * Evalúa si el saldo actual cayó por debajo del límite configurado y dispara el correo de alerta.
     */
        public void verificarYNotificarLimiteSaldo(Usuario usuario, Tarjeta tarjeta) {
            System.out.println("=== VERIFICANDO LIMITE DE SALDO ===");
            System.out.println("Tarjeta: " + tarjeta.getNumeroTarjeta());
            System.out.println("Saldo Actual: " + tarjeta.getSaldo());
            System.out.println("Límite Configurado: " + tarjeta.getLimiteSaldo());

            if (tarjeta.getLimiteSaldo() == null) {
                System.err.println("⚠️ La tarjeta NO tiene un límite de saldo configurado (es null).");
                return;
            }

            if (tarjeta.getSaldo().compareTo(tarjeta.getLimiteSaldo()) <= 0) {
                System.out.println("✅ El saldo es menor o igual al límite. Intentando enviar correo...");
                try {
                    emailService.enviarAlertaSaldoBajo(
                            usuario.getCorreo(),
                            usuario.getNombre(),
                            tarjeta.getNumeroTarjeta(),
                            tarjeta.getSaldo().toString(),
                            tarjeta.getLimiteSaldo().toString()
                    );
                    System.out.println("📧 Correo de alerta de saldo bajo despachado correctamente a " + usuario.getCorreo());
                } catch (Exception e) {
                    System.err.println("❌ Error al enviar alerta de saldo bajo: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("ℹ️ El saldo actual ($" + tarjeta.getSaldo() + ") aún supera el límite ($" + tarjeta.getLimiteSaldo() + ").");
            }
        }

    /**
     * Permite modificar credenciales del usuario con validaciones de rol y duplicación de correo.
     */
    public boolean editarCredenciales(String idUsuarioObjetivo, String nuevoNombre, String nuevoCorreo, String nuevaPassword, Usuario usuarioActual) {
        Optional<Usuario> optUsuario = repositorioUsuarios.findById(idUsuarioObjetivo);
        if (optUsuario.isEmpty()) return false;

        Usuario objetivo = optUsuario.get();
        if (!objetivo.isActivo()) return false;

        String correoAnterior = objetivo.getCorreo();
        boolean huboCambiosCriticos = false;

        if (!Usuario.ROLE_ADMIN.equals(usuarioActual.getRole()) && !usuarioActual.getId().equals(idUsuarioObjetivo)) {
            return false;
        }

        if (nuevoNombre != null && !nuevoNombre.isBlank()) objetivo.setNombre(nuevoNombre);
        
        if (nuevoCorreo != null && !nuevoCorreo.isBlank() && !nuevoCorreo.equals(correoAnterior)) {
            Optional<Usuario> existente = repositorioUsuarios.findByCorreo(nuevoCorreo);
            if (existente.isPresent() && !existente.get().getId().equals(idUsuarioObjetivo)) return false;
            objetivo.setCorreo(nuevoCorreo);
            huboCambiosCriticos = true;
        }
        
        if (nuevaPassword != null && !nuevaPassword.isBlank()) {
            objetivo.setPasswordHash(passwordEncoder.encode(nuevaPassword));
            huboCambiosCriticos = true;
        }

        repositorioUsuarios.save(objetivo);

        if (huboCambiosCriticos) {
            emailService.enviarNotificacionLogin(objetivo.getCorreo(), objetivo.getNombre()); 
            
            if (!correoAnterior.equals(objetivo.getCorreo())) {
                emailService.enviarNotificacionLogin(correoAnterior, objetivo.getNombre());
            }
        }

        return true;
    }

    public CambioCredencialesPendiente prepararCambioCredenciales(
            String idUsuario, String nuevoNombre, String nuevoCorreo, String nuevaPassword) {
        Usuario usuario = repositorioUsuarios.findById(idUsuario)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cuenta activa."));
        String nombreNormalizado = nuevoNombre == null ? "" : nuevoNombre.trim();
        String correoNormalizado = nuevoCorreo == null ? "" : nuevoCorreo.trim();
        if (nombreNormalizado.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }
        if (!correoNormalizado.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Ingresa un correo electrónico válido.");
        }

        repositorioUsuarios.findByCorreo(correoNormalizado)
                .filter(existente -> !existente.getId().equals(idUsuario))
                .ifPresent(existente -> {
                    throw new IllegalArgumentException("Ese correo ya está asociado a otra cuenta.");
                });

        String passwordHash = nuevaPassword == null || nuevaPassword.isBlank()
                ? null
                : passwordEncoder.encode(nuevaPassword);
        return new CambioCredencialesPendiente(
                usuario.getId(), nombreNormalizado, correoNormalizado, passwordHash);
    }

    public boolean aplicarCambioCredenciales(CambioCredencialesPendiente cambio) {
        Optional<Usuario> usuarioOptional = repositorioUsuarios.findById(cambio.idUsuario())
                .filter(Usuario::isActivo);
        if (usuarioOptional.isEmpty()) {
            return false;
        }

        Usuario usuario = usuarioOptional.get();
        String correoAnterior = usuario.getCorreo();
        boolean huboCambioCritico = cambio.passwordHash() != null
                || !correoAnterior.equals(cambio.correo());
        Optional<Usuario> existente = repositorioUsuarios.findByCorreo(cambio.correo());
        if (existente.isPresent() && !existente.get().getId().equals(usuario.getId())) {
            return false;
        }

        usuario.setNombre(cambio.nombre());
        usuario.setCorreo(cambio.correo());
        if (cambio.passwordHash() != null) {
            usuario.setPasswordHash(cambio.passwordHash());
        }
        repositorioUsuarios.save(usuario);

        if (huboCambioCritico) {
            emailService.enviarNotificacionLogin(usuario.getCorreo(), usuario.getNombre());
            if (!correoAnterior.equals(usuario.getCorreo())) {
                emailService.enviarNotificacionLogin(correoAnterior, usuario.getNombre());
            }
        }
        return true;
    }

    public record CambioCredencialesPendiente(
            String idUsuario, String nombre, String correo, String passwordHash)
            implements Serializable {
    }

    /**
     * Vincula una nueva tarjeta simple al usuario.
     */
    public boolean agregarNuevaTarjeta(String idUsuario, String nuevoNumero) {
        Optional<Usuario> opt = repositorioUsuarios.findById(idUsuario);
        if (opt.isPresent()) {
            Usuario u = opt.get();
            boolean existe = u.getTarjetas().stream().anyMatch(t -> t.getNumeroTarjeta().equals(nuevoNumero));
            if (!existe) {
                u.agregarTarjeta(nuevoNumero, BigDecimal.ZERO);
                repositorioUsuarios.save(u);
                return true;
            }
        }
        return false;
    }

    /**
     * Oculta los primeros dígitos de la tarjeta dejando visibles únicamente los últimos 4.
     */
    private String ocultarNumeroTarjeta(String numero) {
        if (numero == null || numero.length() < 4) return "****";
        return "******" + numero.substring(numero.length() - 4);
    }

    /**
     * Vincula una nueva tarjeta verificando la no existencia global en la base de datos.
     */
    public String agregarNuevaTarjetaConValidacion(String idUsuario, String nuevoNumero) {
        Optional<Usuario> dueñoExistente = repositorioUsuarios.findByNumeroTarjetaEnLista(nuevoNumero);
        
        if (dueñoExistente.isPresent()) {
            return "La tarjeta ya está registrada por otro usuario.";
        }

        Optional<Usuario> opt = repositorioUsuarios.findById(idUsuario);
        if (opt.isPresent()) {
            Usuario u = opt.get();
            
            boolean yaLaTiene = u.getTarjetas().stream()
                    .anyMatch(t -> t.getNumeroTarjeta().equals(nuevoNumero));
            
            if (!yaLaTiene) {
                u.agregarTarjeta(nuevoNumero, BigDecimal.ZERO);
                repositorioUsuarios.save(u);
                return "OK";
            } else {
                return "Ya tienes esta tarjeta agregada.";
            }
        }
        return "Usuario no encontrado.";
    }

    /**
     * Remueve una tarjeta vinculada garantizando que el usuario conserve al menos una.
     */
    public boolean eliminarTarjetaDeUsuario(String usuarioId, String numeroTarjeta) {
        return repositorioUsuarios.findById(usuarioId).map(usuario -> {
            if (usuario.getTarjetas().size() > 1) {
                boolean removida = usuario.getTarjetas().removeIf(t -> 
                    t.getNumeroTarjeta().equals(numeroTarjeta));
                
                if (removida) {
                    repositorioUsuarios.save(usuario); 
                    return true;
                }
            }
            return false; 
        }).orElse(false);
    }

    /**
     * Notifica el inicio de sesión por correo electrónico.
     */
    public void procesarNotificacionLogin(String correo) {
        repositorioUsuarios.findByCorreo(correo).ifPresent(u -> {
            try {
                emailService.enviarNotificacionLogin(u.getCorreo(), u.getNombre());
            } catch (Exception e) {
                System.err.println("No se pudo enviar la alerta de inicio de sesión: " + e.getMessage());
            }
        });
    }

    /**
     * Encripta y actualiza la contraseña del usuario.
     */
    public void actualizarPassword(String idUsuario, String nuevaPassword) {
        repositorioUsuarios.findById(idUsuario).ifPresent(usuario -> {
            String passwordEncriptada = passwordEncoder.encode(nuevaPassword);
            usuario.setPasswordHash(passwordEncriptada);
            
            repositorioUsuarios.save(usuario);
            
            try {
                emailService.enviarNotificacionLogin(usuario.getCorreo(), usuario.getNombre());
            } catch (Exception e) {
                System.err.println("No se pudo enviar la notificación de cambio de clave: " + e.getMessage());
            }
        });
    }

    /**
     * Búsqueda simple de usuario por su identificador único ID.
     */
    public Usuario buscarPorId(String id) {
        return repositorioUsuarios.findById(id).orElse(null);
    }

    /**
     * Realiza el cobro de pasaje validando saldo disponible en la tarjeta especificada.
     */
    public boolean cobrarPasaje(String idUsuario, String numeroTarjeta, double monto) {
        Optional<Usuario> opt = repositorioUsuarios.findById(idUsuario);
        if (opt.isEmpty()) return false;

        Usuario u = opt.get();
        return u.getTarjetas().stream()
                .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta))
                .findFirst()
                .map(t -> {
                    BigDecimal montoBD = BigDecimal.valueOf(monto);
                    if (t.getSaldo().compareTo(montoBD) >= 0) {
                        // 1. Calculamos y actualizamos el nuevo saldo en la tarjeta
                        BigDecimal nuevoSaldo = t.getSaldo().subtract(montoBD);
                        t.setSaldo(nuevoSaldo);
                        
                        // 2. Persistimos los cambios en la BD
                        repositorioUsuarios.save(u);
                        
                        // 3. Registramos la transacción
                        servicioTransacciones.registrarTransaccion(u, "Gasto pasaje: " + ocultarNumeroTarjeta(numeroTarjeta), monto);
                        
                        // 4. Enviamos correo de confirmación de gasto
                        emailService.enviarNotificacionGasto(u.getCorreo(), u.getNombre(), monto, nuevoSaldo.toString());
                        
                        // 5. Evaluamos el límite con la tarjeta que ya tiene el nuevo saldo
                        verificarYNotificarLimiteSaldo(u, t);
                        return true;
                    }
                    return false;
                }).orElse(false);
    }

    /**
     * Descuenta un monto de la primera tarjeta del usuario.
     */
    public void gastar(Usuario usuario, double monto) {
        if (usuario != null && usuario.isActivo() && !usuario.getTarjetas().isEmpty()) {
            // Buscamos el usuario en BD para evitar problemas de sincronización de datos
            Usuario u = repositorioUsuarios.findById(usuario.getId()).orElse(usuario);
            Tarjeta t = u.getTarjetas().get(0);
            BigDecimal montoBD = BigDecimal.valueOf(monto);
            
            if (t.getSaldo().compareTo(montoBD) >= 0) {
                BigDecimal nuevoSaldo = t.getSaldo().subtract(montoBD);
                t.setSaldo(nuevoSaldo);
                
                repositorioUsuarios.save(u);
                
                servicioTransacciones.registrarTransaccion(u, "Gasto Directo", monto);
                emailService.enviarNotificacionGasto(u.getCorreo(), u.getNombre(), monto, nuevoSaldo.toString());
                
                verificarYNotificarLimiteSaldo(u, t);
            }
        }
    }

    /**
     * Cobro de pasaje administrativo en la primera tarjeta activa del usuario.
     */
    public boolean cobrarPasajePorAdmin(String idUsuario, double monto) {
        Optional<Usuario> opt = repositorioUsuarios.findById(idUsuario);
        if (opt.isEmpty()) return false;

        Usuario u = opt.get();
        if (!u.isActivo() || u.getTarjetas().isEmpty()) return false;

        Tarjeta t = u.getTarjetas().get(0);
        BigDecimal montoBD = BigDecimal.valueOf(monto);

        if (t.getSaldo().compareTo(montoBD) >= 0) {
            BigDecimal nuevoSaldo = t.getSaldo().subtract(montoBD);
            t.setSaldo(nuevoSaldo);
            
            repositorioUsuarios.save(u);
            
            servicioTransacciones.registrarTransaccion(u, "Cobro pasaje (Admin)", monto);
            emailService.enviarNotificacionGasto(u.getCorreo(), u.getNombre(), monto, nuevoSaldo.toString());
            
            verificarYNotificarLimiteSaldo(u, t);
            return true;
        }
        return false;
    }
}