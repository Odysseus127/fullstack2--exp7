package com.example.jwt_security_api.controller;

import com.example.jwt_security_api.dto.CredentialRequest;
import com.example.jwt_security_api.service.CredentialService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final CredentialService credentialService;

    public UserController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication) {

        return "Welcome USER: "
                + authentication.getName();
    }

    @GetMapping("/dashboard")
    public String dashboard() {

        return "User dashboard accessed successfully";
    }

    @GetMapping("/credentials")
    public List<String> credentialProviders(Authentication authentication) {
        return credentialService.listProviders(authentication.getName());
    }

    @PutMapping("/credentials")
    public ResponseEntity<Void> saveCredential(
            Authentication authentication,
            @Valid @RequestBody CredentialRequest request) {
        credentialService.storeSecret(
                authentication.getName(),
                request.provider(),
                request.secret());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/credentials/{provider}")
    public ResponseEntity<Void> deleteCredential(
            Authentication authentication,
            @PathVariable String provider) {
        credentialService.deleteSecret(authentication.getName(), provider);
        return ResponseEntity.noContent().build();
    }
}
