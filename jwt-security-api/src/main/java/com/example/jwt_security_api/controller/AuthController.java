package com.example.jwt_security_api.controller;

import com.example.jwt_security_api.dto.AuthResponse;
import com.example.jwt_security_api.dto.LoginRequest;
import com.example.jwt_security_api.dto.RefreshRequest;
import com.example.jwt_security_api.dto.RegisterRequest;
import com.example.jwt_security_api.entity.User;
import com.example.jwt_security_api.repository.UserRepository;
import com.example.jwt_security_api.security.JwtService;
import com.example.jwt_security_api.service.RefreshTokenService;//part2

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;//part2
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    //part 2
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsService userDetailsService;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        //part2
        this.refreshTokenService = refreshTokenService;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {

        if (userRepository
                .findByUsername(request.getUsername())
                .isPresent()) {

            return ResponseEntity
                    .badRequest()
                    .body("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setRole("USER");

        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                "User registered successfully"
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getUsername(),
                                request.getPassword()
                        )
                );

        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();
        
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow();

        String accessToken =
                jwtService.generateToken(userDetails);
                
        String refreshToken = refreshTokenService.issue(user);

        return ResponseEntity.ok(
                new AuthResponse(accessToken,refreshToken)
        );
    }

    
    //part2
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request) {
        RefreshTokenService.Rotation rotation =
                refreshTokenService.rotate(request.refreshToken());

        UserDetails userDetails = userDetailsService.loadUserByUsername(
                rotation.user().getUsername()
        );

        String accessToken = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(
                new AuthResponse(accessToken, rotation.refreshToken())
        );
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
    
}
