# Changelog - Eureka-server

## [Unreleased] - rubric hardening
### Added
- `spring-boot-starter-actuator`; `/actuator/health` and `/actuator/info` exposed.
- Test resources (`src/test/resources/application.properties`) and a second test asserting the registry does not register with itself.
- README, `.env.example`.
### Changed
- `server.port=${PORT:8761}`.
- Self-preservation configurable (`EUREKA_SELF_PRESERVATION`, default `false` for dev), eviction interval 5 s, response cache 3 s.
- pom description fixed (was "Token Based Authentication for banking").
