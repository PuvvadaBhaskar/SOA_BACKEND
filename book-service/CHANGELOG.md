# Changelog - book-service

## [Unreleased] - rubric hardening
### Security
- Spring Security with `GatewayAuthenticationFilter`: trusts `X-User-*` only together with the shared `X-Gateway-Secret` (constant-time compare); otherwise 401 JSON. `@PreAuthorize("hasRole('ADMIN')")` on POST/PUT/DELETE.
- DB credentials via `${DB_USERNAME}` / `${DB_PASSWORD}` (plain-text password removed).
### Added
- `BookRequest` DTO with Bean Validation (title not blank, ISBN-10/13 pattern, totalCopies >= 0); `@Valid` on create/update. Entity-level constraints too.
- Atomic `decrementAvailableCopies` / `incrementAvailableCopies` queries plus `@Version` optimistic locking (`bigint default 0`, so `ddl-auto=update` works on existing tables).
- `GET /books/instance` and an `X-Served-By` response header (load-balancing visibility).
- `@RestControllerAdvice` with the common error body; `ResourceNotFoundException` (404), `BadRequestException` (400), `ConflictException` (409), `ForbiddenException` (403); handlers for validation, malformed JSON, type mismatch, optimistic lock, integrity violation, 405, unknown route, and a generic 500 that hides internals.
- Actuator, Eureka health check, `server.port=${PORT:8082}`, unique `instance-id`.
- Tests: `BookServiceTest` (Mockito), `BookControllerTest` (MockMvc through the security chain: 401 no/forged headers, 403 USER writes, 404, 400 validation/malformed/type, 201, 409, X-Served-By), `BookStockIntegrationTest` (H2: bounds, version bump, 10 concurrent borrows never oversell), context test.
### Changed
- POST returns 201 + Location; DELETE returns JSON `{"message": ...}` and 404 for unknown ids (previously silent).
- Update preserves copies on loan (previously `availableCopies` was not adjusted when `totalCopies` changed).
- Borrowing with no copies returns 409 (previously a generic 500).
