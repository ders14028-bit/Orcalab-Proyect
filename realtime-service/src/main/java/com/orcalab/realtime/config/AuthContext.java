package com.orcalab.realtime.config;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthContext {

    public Long usuarioIdActual() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return (Long) principal;
    }

    /** El token JWT crudo de la petición HTTP actual, para reenviarlo a otros servicios en su nombre. */
    public String tokenActual() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        String header = attrs.getRequest().getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        throw new IllegalStateException("No se encontró un token de autenticación en la petición actual");
    }
}
