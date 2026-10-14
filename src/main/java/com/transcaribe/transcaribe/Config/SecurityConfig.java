package com.transcaribe.transcaribe.Config;

import com.transcaribe.transcaribe.Security.JwtAuthenticationFilter;
import com.transcaribe.transcaribe.Security.OAuth2AuthenticationSuccessHandler;
import com.transcaribe.transcaribe.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          PasswordEncoder passwordEncoder,
                          JwtAuthenticationFilter jwtAuthFilter,
                          OAuth2AuthenticationSuccessHandler oauth2SuccessHandler) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtAuthFilter = jwtAuthFilter;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/mapa-rutas",
                    "/mapa-rutas/api/**",
                    "/Rutas/**",
                    "/login",
                    "/registro",
                    "/verificar-otp/**",
                    "/olvido-password",
                    "/verificar-codigo-recuperar-password/**",
                    "/validar-codigo-restablecimiento",
                    "/restablecer-password",
                    "/confirmar-restablecimiento",
                    "/css/**",
                    "/error/**",
                    "/images/**",
                    "/js/**"
                ).permitAll()
                .requestMatchers("/choose-view").authenticated()
                .requestMatchers("/chat/**").hasAnyRole("ADMIN", "CONDUCTOR")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/conductor/**").hasRole("CONDUCTOR")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler(oauth2SuccessHandler)
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("correo")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    var roles = authentication.getAuthorities();
                    boolean isAdmin = roles.stream().anyMatch(r ->
                        r.getAuthority().equals("ROLE_ADMIN"));
                    boolean isConductor = roles.stream().anyMatch(r ->
                        r.getAuthority().equals("ROLE_CONDUCTOR"));

                    if (isAdmin || isConductor) {
                        response.sendRedirect("/choose-view");
                    } else {
                        response.sendRedirect("/menu");
                    }
                })
                .failureHandler((request, response, exception) -> {
                    if (exception instanceof DisabledException ||
                        (exception.getCause() != null && exception.getCause() instanceof DisabledException)) {
                        String correo = request.getParameter("correo");
                        response.sendRedirect("/verificar-otp?correo=" + correo);
                    } else {
                        response.sendRedirect("/login?error=true");
                    }
                })
                .permitAll()
            )

            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .deleteCookies("JWT_TOKEN")
                .invalidateHttpSession(true)
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)
                .expiredUrl("/login?expired=true")
            )
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/error/403")
            );
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider auth = new DaoAuthenticationProvider();
        auth.setUserDetailsService(userDetailsService);
        auth.setPasswordEncoder(passwordEncoder);
        return auth;
    }
}