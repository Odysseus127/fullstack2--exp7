# JWT Security API

JWT Security API is a Spring Boot application with a small browser-based workspace. It demonstrates stateless authentication, role-protected routes, refresh-token rotation, and encrypted storage for user-provided third-party credentials.

## What the application does

- Serves a sign-in and account-registration page, then an authenticated workspace with overview, profile, credentials, and (for administrators) user-directory screens.
- Registers users with the `USER` role and stores their passwords as BCrypt hashes.
- Authenticates users and issues short-lived signed JWT access tokens.
- Issues refresh tokens for maintaining sessions. The raw refresh token is returned to the client, but only its SHA-256 hash is stored in the database. Refresh rotates the token; logout revokes it.
- Lets each user save one secret per provider. Credential secrets are encrypted with AES-GCM before storage. The credentials page shows provider names only; it does not show saved secrets.
- Restricts `/api/users/**` to authenticated users with `USER` or `ADMIN` roles, and `/api/admin/**` to `ADMIN`.

Credential storage is not itself a connection to GitHub or another provider. Backend code for a provider integration can retrieve a user's decrypted secret through `CredentialService.getSecretForBackendUse(...)`; the project currently does not make external provider requests.

## Technology

- Java 25 and Spring Boot
- Spring Web MVC, Spring Security, Spring Data JPA, and Jakarta Bean Validation
- MySQL
- JWT signing and verification via JJWT
- Browser UI built with HTML, CSS, and JavaScript

## Requirements and configuration

You need Java 25, MySQL, and the included Maven wrapper. Create the database configured by `spring.datasource.url` in `src/main/resources/application.properties` (default database name: `jwt_security_api_db`), and configure the datasource username and password for your local MySQL instance.

Set two independent Base64-encoded keys in the environment before starting the app:

- `JWT_SECRET_BASE64`: at least 32 cryptographically random bytes, Base64-encoded, for signing access tokens.
- `AES_KEY_BASE64`: exactly 32 cryptographically random bytes, Base64-encoded, for AES-256-GCM credential encryption.

Keep both keys private and out of source control. Changing the AES key makes previously stored credentials impossible to decrypt unless you first migrate them. The current access-token lifetime is 15 minutes and refresh-token lifetime is 7 days.

For example, after securely generating and Base64-encoding two separate keys, set them in PowerShell and start the application from the project directory:

```powershell
$env:JWT_SECRET_BASE64 = "<your Base64-encoded 32-byte JWT key>"
$env:AES_KEY_BASE64 = "<your Base64-encoded 32-byte AES key>"
.\mvnw.cmd spring-boot:run
```

The application listens on `http://localhost:8080`. Hibernate is configured with `ddl-auto=update` and creates or updates the mapped tables. SQL logging is enabled in the supplied properties.

## Browser workspace

Open `http://localhost:8080`:

1. Create an account and then sign in.
2. Use **Overview** and **My profile** to view authenticated workspace content.
3. Use **Credentials** to save a provider name and secret, view saved provider names, or remove a saved credential.
4. Sign out with the sidebar button.

The frontend keeps the access and refresh tokens in `sessionStorage`. It sends the access token as a Bearer token for protected API calls and attempts refresh when a request is unauthorized. This is a teaching/demo application; production deployments should review browser token storage, HTTPS, CSRF/CORS settings, key management, and operational logging for their threat model.

## API reference

All endpoints use `http://localhost:8080` as the base URL. JSON request bodies use `Content-Type: application/json`. For protected routes, send:

```http
Authorization: Bearer <access-token>
```

### Authentication

| Method and path | Purpose | Access |
| --- | --- | --- |
| `POST /api/auth/register` | Create a `USER` account. Body: `{"username":"alice","password":"..."}` | Public |
| `POST /api/auth/login` | Authenticate and return `token` and `refreshToken` | Public |
| `POST /api/auth/refresh` | Rotate a refresh token. Body: `{"refreshToken":"..."}`; returns replacement tokens | Public |
| `POST /api/auth/logout` | Revoke a refresh token. Body: `{"refreshToken":"..."}` | Public |

### User routes

All `/api/users/**` routes require a valid access token for a `USER` or `ADMIN`.

| Method and path | Purpose |
| --- | --- |
| `GET /api/users/profile` | Return the authenticated user's profile greeting |
| `GET /api/users/dashboard` | Access the user dashboard |
| `GET /api/users/credentials` | List this user's saved provider names only |
| `PUT /api/users/credentials` | Save or update a credential. Body: `{"provider":"GitHub","secret":"..."}` |
| `DELETE /api/users/credentials/{provider}` | Delete this user's credential for the provider |

Saving and deleting credentials return `204 No Content`. Provider names are trimmed and limited to letters, numbers, spaces, dots, underscores, and hyphens. The secret is required and limited to 700 characters. The authenticated identity determines ownership; clients cannot specify another user's username.

### Admin routes

All `/api/admin/**` routes require the `ADMIN` role.

| Method and path | Purpose |
| --- | --- |
| `GET /api/admin/dashboard` | Access the admin dashboard |
| `GET /api/admin/users` | List user IDs, usernames, and roles |
| `DELETE /api/admin/users/{id}` | Delete a user by ID |

Public registration always assigns `USER`. To exercise administrator functionality in a local development database, promote a test account's role to `ADMIN` using a controlled database operation. Do not expose role assignment through public registration.

## Data and security model

- `users`: username, BCrypt password hash, and role.
- `refresh_tokens`: owning user, unique SHA-256 refresh-token hash, expiration time, and revocation state.
- `stored_credentials`: owning user, provider, and encrypted secret. A database uniqueness constraint permits one credential for each user/provider pair.
- AES-GCM encrypted credential values include a format version, nonce, and ciphertext. Decryption is for trusted backend use; API responses and the browser UI do not return credential secrets.
- Access JWTs carry the username as their subject and are verified by the JWT authentication filter. Spring Security is configured for stateless sessions.

## Build and tests

Compile:

```powershell
.\mvnw.cmd -DskipTests compile
```

Run the focused credential-service tests:

```powershell
.\mvnw.cmd -Dtest=CredentialServiceTest test
```

The credential-service tests verify encrypted-value handling through the service boundary, backend retrieval, provider-only listing, and deletion. The existing Spring context-load test requires the database and both key environment variables to be configured.
