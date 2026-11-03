# Changelog - fine-service

## [Unreleased] - rubric hardening
### Security
- Spring Security with `GatewayAuthenticationFilter` (requires `X-Gateway-Secret` + `X-User-*`), `@PreAuthorize` on ADMIN endpoints, owner-or-ADMIN checks in the service (403).
- DB credentials via environment variables.
### Added
- Full CRUD: `POST /fines` (ADMIN manual fine), `GET /fines` (ADMIN, optional `status`), `GET /fines/me`, `PUT /fines/{id}` (mark PAID/WAIVED/UNPAID, change amount), `DELETE /fines/{id}`, `GET /fines/instance`, `X-Served-By` header.
- `FineStatus` enum, `paidDate` and `reason` columns.
- Idempotent `/fines/calculate` (one fine per borrow), so borrow-service can retry safely.
- `spring-boot-starter-validation`; `FineCreateRequest` / `FineUpdateRequest` with Bean Validation; `@Valid` on bodies.
- Common error handler incl. invalid dates (400) and enum values (400).
- Actuator, Eureka health check, `server.port=${PORT:8084}`, unique `instance-id`, `fine.per-day` property.
- Tests: `FineServiceTest` (late/on-time, idempotency, ownership, PAID rules, amount changes, 404), `FineControllerTest` (401/403/404/400/200), context test with real H2 idempotency check.
