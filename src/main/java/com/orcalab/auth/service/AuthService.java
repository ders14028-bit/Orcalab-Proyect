package com.orcalab.auth.service;

import com.orcalab.auth.dto.AuthResponse;
import com.orcalab.auth.dto.LoginRequest;
import com.orcalab.auth.dto.RegisterRequest;
import com.orcalab.auth.exception.AuthException;
import com.orcalab.auth.model.Role;
import com.orcalab.auth.model.User;
import com.orcalab.auth.repository.UserRepository;
import com.orcalab.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-01: registro. HU-02: login. HU-03: el rol se asigna en el registro
 * (por defecto PUBLICO; INVESTIGADOR/ORGANIZACION/ADMIN se asignan por un
 * flujo administrativo aparte, fuera de alcance de esta HU).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException("El correo ya esta registrado");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(Role.PUBLICO)
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException("Credenciales invalidas"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException("Credenciales invalidas");
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), jwtUtil.getExpirationMs());
    }
}
