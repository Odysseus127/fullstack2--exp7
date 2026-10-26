//part2:encrypt stored 3rd party credentials in database
package com.example.jwt_security_api.entity;
import jakarta.persistence.*;
@Entity
@Table(name = "stored_credentials",
         uniqueConstraints = {
            @UniqueConstraint(columnNames = {"user_id","provider"})
        }
)
public class StoredCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(nullable = false, length = 4000)
    private String encryptedSecret;

    protected StoredCredential() {
    }

    public StoredCredential(User user, String provider, String encryptedSecret) {
        this.user = user;
        this.provider = provider;
        this.encryptedSecret = encryptedSecret;
    }
    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getProvider() {
        return provider;
    }

    public String getEncryptedSecret() {
        return encryptedSecret;
    }

    public void setEncryptedSecret(String encryptedSecret) {
        this.encryptedSecret = encryptedSecret;
    }
    
}
