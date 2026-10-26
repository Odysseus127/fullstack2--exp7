//part2
package com.example.jwt_security_api.service;

import com.example.jwt_security_api.entity.StoredCredential;
import com.example.jwt_security_api.entity.User;
import com.example.jwt_security_api.repository.StoredCredentialRepository;
import com.example.jwt_security_api.repository.UserRepository;
import com.example.jwt_security_api.security.AesEncryptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service 
public class CredentialService {
    private final StoredCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final AesEncryptionService encryptionService;

    public CredentialService(
            StoredCredentialRepository credentialRepository,
            UserRepository userRepository,
            AesEncryptionService encryptionService) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.encryptionService = encryptionService;
    }
    @Transactional
    public void storeSecret(String username, String provider, String secret) {
        String normalizedProvider = normalizeProvider(provider);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        String encryptedSecret = encryptionService.encrypt(secret);

        StoredCredential credential = credentialRepository
                .findByUserUsernameAndProvider(username, normalizedProvider)
                .orElseGet(() -> new StoredCredential(user, normalizedProvider, encryptedSecret));

        credential.setEncryptedSecret(encryptedSecret);
        credentialRepository.save(credential);
    }

    @Transactional(readOnly = true)
    public String getSecretForBackendUse(String username, String provider) {
        StoredCredential credential = credentialRepository
                .findByUserUsernameAndProvider(username, normalizeProvider(provider))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Credential not found"));

        return encryptionService.decrypt(credential.getEncryptedSecret());
    }

    @Transactional(readOnly = true)
    public List<String> listProviders(String username) {
        return credentialRepository.findProviderNamesByUsername(username);
    }

    @Transactional
    public void deleteSecret(String username, String provider) {
        StoredCredential credential = credentialRepository
                .findByUserUsernameAndProvider(username, normalizeProvider(provider))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Credential not found"));
        credentialRepository.delete(credential);
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider is required");
        }
        String normalizedProvider = provider.trim();
        if (normalizedProvider.isEmpty()
                || normalizedProvider.length() > 100
                || !normalizedProvider.matches("[A-Za-z0-9][A-Za-z0-9._ -]*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid provider name");
        }
        return normalizedProvider;
    }
}
