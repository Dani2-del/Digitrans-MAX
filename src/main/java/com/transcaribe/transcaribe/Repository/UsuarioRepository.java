package com.transcaribe.transcaribe.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.transcaribe.transcaribe.Model.Usuario;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByCorreo(String correo);

    // Búsqueda paginada por nombre o correo (usada en el dashboard admin)
    Page<Usuario> findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(
            String nombre, String correo, Pageable pageable);

    List<Usuario> findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(
            String nombre, String correo);

    List<Usuario> findByRoleAndActivoTrue(String role);

    Optional<Usuario> findByIdAndActivoTrue(String id);

    @Query("{}")
    Stream<Usuario> streamAll();

    @Query("{ 'rutas_favoritas': { $regex: ?0, $options: 'i' } }")
    List<Usuario> findByRutaFavoritaIgnoreCase(String rutaRegex);

    @Query("{ 'tarjetas.numeroTarjeta': ?0 }")
    Optional<Usuario> findByNumeroTarjetaEnLista(String numeroTarjeta);
}