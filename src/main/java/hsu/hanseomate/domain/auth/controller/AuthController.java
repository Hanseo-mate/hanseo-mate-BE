package hsu.hanseomate.domain.auth.controller;

import hsu.hanseomate.domain.auth.dto.AuthResponse;
import hsu.hanseomate.domain.auth.dto.LoginRequest;
import hsu.hanseomate.domain.auth.dto.SignupRequest;
import hsu.hanseomate.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity.status(201).body(authService.signup(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
