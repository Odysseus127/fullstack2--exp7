package com.example.jwt_security_api.service;

import com.example.jwt_security_api.entity.StoredCredential;
import com.example.jwt_security_api.entity.User;
import com.example.jwt_security_api.repository.StoredCredentialRepository;
import com.example.jwt_security_api.repository.UserRepository;
import com.example.jwt_security_api.security.AesEncryptionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialServiceTest {
    private final StoredCredentialRepository credentialRepository =
            mock(StoredCredentialRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AesEncryptionService encryptionService = mock(AesEncryptionService.class);
    private final CredentialService credentialService = new CredentialService(
            credentialRepository, userRepository, encryptionService);

    @Test
    void storesEncryptedSecretAndListsOnlyProviderNames() {
        User user = new User("alice", "password-hash", "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(encryptionService.encrypt("api-key")).thenReturn("encrypted-api-key");
        when(credentialRepository.findByUserUsernameAndProvider("alice", "GitHub"))
                .thenReturn(Optional.empty());
        when(credentialRepository.findProviderNamesByUsername("alice"))
                .thenReturn(List.of("GitHub"));

        credentialService.storeSecret("alice", " GitHub ", "api-key");

        verify(credentialRepository).save(org.mockito.ArgumentMatchers.argThat(credential ->
                credential.getUser() == user
                        && credential.getProvider().equals("GitHub")
                        && credential.getEncryptedSecret().equals("encrypted-api-key")));
        assertEquals(List.of("GitHub"), credentialService.listProviders("alice"));
    }

    @Test
    void decryptsCredentialForBackendUse() {
        User user = new User("alice", "password-hash", "USER");
        StoredCredential credential = new StoredCredential(user, "GitHub", "encrypted-api-key");
        when(credentialRepository.findByUserUsernameAndProvider("alice", "GitHub"))
                .thenReturn(Optional.of(credential));
        when(encryptionService.decrypt("encrypted-api-key")).thenReturn("api-key");

        assertEquals("api-key", credentialService.getSecretForBackendUse("alice", "GitHub"));
    }

    @Test
    void deletesCredentialForTheAuthenticatedUser() {
        User user = new User("alice", "password-hash", "USER");
        StoredCredential credential = new StoredCredential(user, "GitHub", "encrypted-api-key");
        when(credentialRepository.findByUserUsernameAndProvider("alice", "GitHub"))
                .thenReturn(Optional.of(credential));

        credentialService.deleteSecret("alice", "GitHub");

        verify(credentialRepository).delete(credential);
    }
}
