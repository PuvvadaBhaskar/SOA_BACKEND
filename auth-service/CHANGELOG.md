# Changelog - auth-service

## [Unreleased] - rubric hardening
### Security
- JWT secret moved from source to `jwt.secret=${JWT_SECRET:...}`; issuer (`iss`) and `userId` claims added; expiry configurable.
- DB credentials moved to `${DB_USERNAME:postgres}` / `${DB_PASSWORD:changeme}` (the previous plain-text password was removed).
- `role` removed from `RegisterRequest`: every registration is USER.
- Seeded ADMIN (`AdminAccountInitializer`) from `admin.*` properties, BCrypt-hashed.
- Security config: stateless, CSRF/basic/form login disabled, JSON 401/403.
### Added
- `GET /auth/validate`.
- Validation: `@Valid` on login; `@NotBlank`/`@Size(min=8)` passwords; username pattern; email length.
- `@RestControllerAdvice` with `{timestamp,status,error,message,path}` (+`fieldErrors`); custom `ConflictException` (409), `InvalidCredentialsException` (401), `InvalidTokenException` (401), `BadRequestException`, `ResourceNotFoundException`.
- Register returns 201 with `{id, username, email, role, message}`; login returns `tokenType`, `expiresIn`, `userId`.
- Actuator (`health,info`), Eureka health check, `server.port=${PORT:8081}`, unique `instance-id`.
- Tests: `JwtServiceTest` (claims, expiry, tampering, foreign key, wrong issuer, short secret), `AuthServiceTest` (Mockito), `AuthControllerIntegrationTest` (MockMvc + security + H2: 201/400/409/401/validate), `AdminAccountInitializerTest`, context test (asserts BCrypt admin seeded).
- H2 test dependency, README, `.env.example`.
### Fixed
- The existing test was in package `com.archivalia.eureka`, so `@SpringBootTest` could not find the application class. It is now in `com.archivalia.auth`.
