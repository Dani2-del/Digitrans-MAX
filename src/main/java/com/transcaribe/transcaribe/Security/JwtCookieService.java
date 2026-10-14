package com.transcaribe.transcaribe.Security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

@Service
public class JwtCookieService {

    public static final String JWT_COOKIE_NAME = "JWT_TOKEN";
    private final int cookieExpiry = 86400; 

    public void addJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, token);
        cookie.setHttpOnly(true); 
        cookie.setSecure(false);  
        cookie.setPath("/");
        cookie.setMaxAge(cookieExpiry);
        response.addCookie(cookie);
    }

    public void clearCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}