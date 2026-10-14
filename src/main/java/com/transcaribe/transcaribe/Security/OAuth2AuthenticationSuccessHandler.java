
package com.transcaribe.transcaribe.Security;

import java.io.IOException;
import java.util.Optional;


import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final UsuarioRepository userRepository;

    public OAuth2AuthenticationSuccessHandler(
            JwtService jwtService,
            JwtCookieService jwtCookieService,
            UsuarioRepository userRepository) {
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null) throw new RuntimeException("Google no retornó email");

        Optional<Usuario> existingUser = userRepository.findByCorreo(email);
        Usuario usuario;

        if (existingUser.isPresent()) {
            usuario = existingUser.get();
        } else {
            usuario = new Usuario(email, "", name != null ? name : email);
            usuario.setVerificado(true); 
            userRepository.save(usuario);
        }
        var userDetails = User.withUsername(usuario.getCorreo())
                .password("")
                .authorities(usuario.getRole()) 
                .build();
        String token = jwtService.generateToken(userDetails);
        jwtCookieService.addJwtCookie(response, token);
        var authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        boolean esAdmin = Usuario.ROLE_ADMIN.equals(usuario.getRole());
        boolean esConductor = Usuario.ROLE_CONDUCTOR.equals(usuario.getRole());

        if (esAdmin || esConductor) {
            response.sendRedirect("/choose-view");
        } else {
            response.sendRedirect("/menu");
        }
    }
}