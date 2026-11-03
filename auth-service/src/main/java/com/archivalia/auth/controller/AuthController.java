package com.archivalia.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.archivalia.auth.dto.AuthResponse;
import com.archivalia.auth.dto.LoginRequest;
import com.archivalia.auth.dto.RegisterRequest;
import com.archivalia.auth.dto.RegisterResponse;
import com.archivalia.auth.dto.TokenValidationResponse;
import com.archivalia.auth.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 201 on success, 400 on validation errors, 409 on duplicate username/email. */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                authService.register(request)
        );
    }

    /** 200 with a JWT, 400 on validation errors, 401 on bad credentials. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    /** 200 with the token's identity, 401 when missing/invalid/expired. */
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        return ResponseEntity.ok(
                authService.validate(authorization)
        );
    }
}
