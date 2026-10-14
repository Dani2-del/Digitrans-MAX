package com.transcaribe.transcaribe.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;

@Repository
public interface TransaccionRepository extends MongoRepository<Transaccion, String> {
    List<Transaccion> findByUsuario(Usuario usuario);

    @Query("{}")
    Stream<Transaccion> streamAll();

    @Query("{ 'fecha': { '$gte': ?0, '$lt': ?1 } }")
    Stream<Transaccion> streamByFechaEnRango(LocalDateTime inicio, LocalDateTime fin);
}

//Repositorios , No hay nada del otro mundo aqui

//⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿
//⣿⣿⣿⣿⣿⣿⣿⣿⡿⠿⠛⠛⠛⢻⡻⠿⣿⣿⣿⣿⣿⣿⣿⣿
//⣿⣿⣿⣿⣿⣿⣟⣫⡾⠛⠛⠛⠛⠛⠛⠿⣾⣽⡻⣿⣿⣿⣿⣿
//⣿⣿⣿⣿⣿⡟⣼⠏⠄⠄⠄⠄⠄⠄⣀⣀⡀⣙⣿⣎⢿⣿⣿⣿
//⣿⣿⣿⣿⣿⢹⡟⠄⠄⠄⣰⡾⠟⠛⠛⠛⠛⠛⠛⠿⣮⡻⣿⣿
//⣿⡿⢟⣻⣟⣽⠇⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠄⠄⠄⠈⢿⡹⣿
//⡟⣼⡟⠉⠉⣿⠄⠄⠄⠄⢿⡄⠄⠄⠄⠄⠄⠄⠄⠄⠄⣼⢟⣿
//⣇⣿⠁⠄⠄⣿⠄⠄⠄⠄⠘⢿⣦⣄⣀⣀⣀⣀⣤⡴⣾⣏⣾⣿
//⡇⣿⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠈⠉⠛⠋⠉⠉⠄⠄⢻⣿⣿⣿
//⢃⣿⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⢸⣧⣿⣿
//⡻⣿⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⣸⣧⣿⣿
//⡇⣿⠄⠄⠄⣿⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⠄⣿⢹⣿⣿
//⣿⡸⢷⣤⣤⣿⡀⠄⠄⠄⠄⢠⣤⣄⣀⣀⣀⠄⠄⢠⣿⣿⣿⣿
//⣿⣿⣷⣿⣷⣿⡇⠄⠄⠄⠄⢸⡏⡍⣿⡏⠄⠄⠄⢸⡏⣿⣿⣿
//⣿⣿⣿⣿⣿⢼⡇⠄⠄⠄⠄⣸⡇⣷⣻⣆⣀⣀⣀⣼⣻⣿⣿⣿
//⣿⣿⣿⣿⣿⣜⠿⢦⣤⣤⡾⢟⣰⣿⣷⣭⣯⣭⣯⣥⣿⣿⣿⣿
//⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿