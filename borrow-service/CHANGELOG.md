# Changelog - borrow-service

## [Unreleased] - rubric hardening
### Security
- `userId` removed from `BorrowRequest`: the borrower is always `X-User-Id` from the gateway. USER sees/returns/extends only own borrows (403 otherwise); ADMIN sees all.
- Spring Security with `GatewayAuthenticationFilter` (requires `X-Gateway-Secret` + `X-User-*`), `@PreAuthorize` per endpoint.
- Feign forwards `X-User-*` + `X-Gateway-Secret` so internal calls pass book/fine-service security.
- DB credentials via environment variables.
### Added
- `PUT /borrows/{id}/extend`, `DELETE /borrows/{id}` (ADMIN), `GET /borrows/me`, `GET /borrows?status=`, `GET /borrows/instance`, `X-Served-By` header.
- Status enum `PENDING/BORROWED/RETURNED/FAILED` (stored as text, compatible with existing rows); new columns `extensionCount`, `fineId`, `finePending`, `failureReason`.
- Saga-style consistency: PENDING -> BORROWED / FAILED, compensation on a failed final save, return never lost when fine-service is down (`finePending` + `FineReconciler`).
- Return response `{borrow, fine, warning}` with fine details.
- `spring-cloud-starter-circuitbreaker-resilience4j`: circuit breaker + time limiter on Feign clients; `SafeRetryer`; fallback factories throwing 503 `ServiceUnavailableException`; `RemoteCallErrors` mapping.
- Bean Validation (`bookId` not null/positive, extend `days` 1-30), common error handler (+ FeignException safety net).
- Actuator, Eureka health check, `server.port=${PORT:8083}`, unique `instance-id`, `@EnableScheduling`.
- Tests: `BorrowServiceTest` (PENDING->BORROWED, FAILED on 409/503, header forwarding, return with fine, return with fine-service down, book-service down, ownership 403, extend rules, delete rules, reconciler), `RemoteCallErrorsTest`, `SafeRetryerTest`, `BorrowControllerTest` (401/400/403/404/503, userId from header not body), context test (Feign clients, circuit breaker factory, retryer).
### Changed
- POST returns 201 + Location; errors are now 404/409/400/503 instead of generic 500s.
