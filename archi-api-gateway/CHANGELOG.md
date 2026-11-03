# Changelog - archi-api-gateway

## [Unreleased] - rubric hardening
### Added
- `JwtAuthenticationFilter` (GlobalFilter, jjwt 0.12.6): public login/register, 401 JSON for missing/invalid/expired tokens, 403 JSON for insufficient role, strips and re-sets `X-User-Id/Name/Role` plus `X-Gateway-Secret`.
- `RouteAuthorizationRules`: ordered, deny-by-default role table for book/borrow/fine.
- `JwtValidator`: verifies signature and `iss`, requires `sub`, `role` and `userId`.
- jjwt-api/impl/jackson 0.12.6 dependencies.
- Tests: `JwtAuthenticationFilterTest` (pass-through, 401 missing/malformed/tampered/foreign-key/expired/wrong-issuer/missing-claim, 403, header stripping, path tricks), `RouteAuthorizationRulesTest`, context test.
- README, `.env.example`.
### Changed
- `server.port=${PORT:8080}`, `eureka.instance.instance-id=${spring.application.name}:${random.value}`, fast registry refresh (5 s), LoadBalancer cache TTL 5 s, Eureka health checks.
- **Discovery locator disabled** (it would bypass the authorization rules).
- Actuator `health,info` exposed.
