package com.transcaribe.transcaribe.service;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {

        Usuario user = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + correo));

        String role = user.getRole().startsWith("ROLE_") 
                ? user.getRole() 
                : "ROLE_" + user.getRole();


        return new User(
                user.getCorreo(),
                user.getPasswordHash(),
                user.isVerificado(), 
                true,                
                true,                
                true,                
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
    }
}