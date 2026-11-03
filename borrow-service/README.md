# borrow-service: borrowing workflow (port 8083)

Part of the **Archivalia Digital Library** microservices system: Eureka-server, archi-api-gateway, auth-service, book-service, borrow-service, fine-service.

| Endpoint | Role | Notes |
|---|---|---|
| `POST /borrows` `{bookId}` | USER, ADMIN | borrower = `X-User-Id`; 201; 404 unknown book; 409 no copies / already borrowed; 503 book-service down |
| `PUT /borrows/{id}/return` | owner, ADMIN | returns `{borrow, fine, warning}` |
| `PUT /borrows/{id}/extend` `{days?}` | owner, ADMIN | max 2 extensions, not when overdue |
| `GET /borrows/me`, `GET /borrows/{id}`, `GET /borrows/user/{userId}` | owner, ADMIN | 403 for other users |
| `GET /borrows?status=` | ADMIN | all borrows |
| `DELETE /borrows/{id}` | ADMIN | 409 while the book is still borrowed |
| `GET /borrows/instance` | any authenticated | load-balancing demo |

### Consistency design (documented choice)
* **Borrow**: save `PENDING` -> book-service decrements stock (atomic) -> save `BORROWED`. If book-service refuses
  or is unavailable the record becomes `FAILED` (with `failureReason`) and the caller gets 404/409/503. If the
  final save fails after stock was taken, the copy is handed back (compensation).
* **Return**: book-service increments stock **first**. If it is down, the return fails with 503 and nothing changes.
  Then the borrow is saved `RETURNED`, and only then is fine-service asked for the fine.
  **If fine-service fails, the return still succeeds**: the borrow is flagged `finePending=true`, the response has
  `"fine": null` and a `warning`, and `FineReconciler` retries every 60 s. Fine calculation is idempotent per
  borrow, so retries never double-charge.
### Resilience (Resilience4j + Feign)
* Circuit breaker per Feign method (50% failure rate over 10 calls, min 5, open 10 s); 4xx responses are business
  errors and do not trip it.
* Time limiter: 8 s per call. Feign connect/read timeouts 2 s / 3 s.
* Retry: `SafeRetryer` (3 attempts, 200 ms -> 1 s back-off). GET is retried on any I/O error; PUT/POST only when the
  connection was refused, so stock is never decremented twice. Each retry is load-balanced again.
* Fallbacks (`BookClientFallbackFactory`, `FineClientFallbackFactory`) never invent data: they rethrow downstream
  404/409/400 and turn outages, timeouts and an open circuit into **503 Service Unavailable**.
* Feign forwards the caller's `X-User-*` headers plus `X-Gateway-Secret`. They are passed as an explicit header
  map because the time limiter runs calls on another thread, where the incoming request is not visible.

See `CHANGELOG.md` for what changed in this version.

---

## Running the whole Archivalia system

### 1. Prerequisites
* JDK 17+ (the gateway targets Java 21), Maven wrapper included (`./mvnw` / `mvnw.cmd`).
* PostgreSQL with four databases:
  ```sql
  CREATE DATABASE auth_db; CREATE DATABASE book_db; CREATE DATABASE borrow_db; CREATE DATABASE fine_db;
  ```

### 2. Environment variables
Every value has a safe **development default**, so the system starts without any variables.
Set them for anything that is not a local demo. `JWT_SECRET` and `GATEWAY_SECRET` **must be identical**
in every service that uses them.

| Variable | Used by | Default | Purpose |
|---|---|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | auth, book, borrow, fine | `postgres` / `changeme` | PostgreSQL credentials |
| `DB_URL` | auth, book, borrow, fine | `jdbc:postgresql://localhost:5432/<service>_db` | JDBC URL |
| `JWT_SECRET` | auth-service, gateway | dev-only value | HMAC key that signs/validates JWTs (>= 32 bytes) |
| `JWT_ISSUER` | auth-service, gateway | `archivalia-auth-service` | `iss` claim written and required |
| `JWT_EXPIRATION_MS` | auth-service | `3600000` (1 h) | token lifetime |
| `GATEWAY_SECRET` | gateway, book, borrow, fine | dev-only value | shared secret proving a request came through the gateway |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` | auth-service | `admin` / `Admin@12345` / `admin@archivalia.local` | seeded administrator |
| `EUREKA_URL` | all clients | `http://localhost:8761/eureka/` | registry address |
| `PORT` | all | 8761 / 8080 / 8081 / 8082 / 8083 / 8084 | HTTP port (or `-Dserver.port=` / `--server.port=`) |
| `FINE_PER_DAY` | fine-service | `10.0` | fine per late day |
| `EUREKA_SELF_PRESERVATION` | Eureka-server | `false` | keep `false` for local load-balancing demos |

Each project has a `.env.example`. Spring does not read `.env` files itself, so export the values first:
```bash
set -a; source .env; set +a        # bash / Git Bash
```
```powershell
Get-Content .env | ? { $_ -match '^[^#].*=' } | % { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }   # PowerShell
```

### 3. Start order
1. `Eureka-server` (8761), then wait until http://localhost:8761 is up
2. `auth-service` (8081)
3. `book-service` (8082), plus a second instance for load balancing
4. `fine-service` (8084)
5. `borrow-service` (8083), plus a second instance for load balancing
6. `archi-api-gateway` (8080): **the only port clients should use**

```bash
cd Eureka-server && ./mvnw spring-boot:run          # one terminal per service
```

### 4. Sample calls (all through the gateway on :8080)
```bash
# Register (always role USER; a "role" field in the body is ignored)        -> 201
curl -i -X POST localhost:8080/auth/register -H "Content-Type: application/json" \
     -d '{"username":"alice","email":"alice@example.com","password":"password123"}'

# Duplicate username/email                                                    -> 409
# Bad body (blank username, invalid email, short password)                    -> 400 with fieldErrors

# Login                                                                       -> 200 {token, userId, role, ...}
USER_TOKEN=$(curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
     -d '{"username":"alice","password":"password123"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
ADMIN_TOKEN=$(curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"Admin@12345"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# Wrong password                                                              -> 401
curl -i -X POST localhost:8080/auth/login -H "Content-Type: application/json" -d '{"username":"alice","password":"wrongpass1"}'

# Validate a token                                                            -> 200 / 401
curl -s localhost:8080/auth/validate -H "Authorization: Bearer $USER_TOKEN"

# No token                                                                    -> 401 JSON
curl -i localhost:8080/books

# Admin-only call as USER                                                     -> 403 JSON
curl -i -X POST localhost:8080/books -H "Authorization: Bearer $USER_TOKEN" -H "Content-Type: application/json" \
     -d '{"title":"Effective Java","author":"J. Bloch","isbn":"978-0134685991","category":"Programming","totalCopies":3}'

# Admin-only call as ADMIN                                                    -> 201
curl -i -X POST localhost:8080/books -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
     -d '{"title":"Effective Java","author":"J. Bloch","isbn":"978-0134685991","category":"Programming","totalCopies":3}'

# Borrow as USER (userId comes from the token, never from the body)          -> 201
curl -i -X POST localhost:8080/borrows -H "Authorization: Bearer $USER_TOKEN" -H "Content-Type: application/json" -d '{"bookId":1}'
curl -s localhost:8080/borrows/me            -H "Authorization: Bearer $USER_TOKEN"
curl -s -X PUT localhost:8080/borrows/1/extend -H "Authorization: Bearer $USER_TOKEN"
curl -s -X PUT localhost:8080/borrows/1/return -H "Authorization: Bearer $USER_TOKEN"   # includes fine details
curl -s localhost:8080/fines/me              -H "Authorization: Bearer $USER_TOKEN"

# ADMIN-only management
curl -s localhost:8080/borrows               -H "Authorization: Bearer $ADMIN_TOKEN"   # all borrows
curl -s localhost:8080/fines                 -H "Authorization: Bearer $ADMIN_TOKEN"   # all fines
curl -s -X PUT localhost:8080/fines/1 -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"status":"PAID"}'
curl -s -X DELETE localhost:8080/fines/1     -H "Authorization: Bearer $ADMIN_TOKEN"
curl -s -X DELETE localhost:8080/borrows/1   -H "Authorization: Bearer $ADMIN_TOKEN"

# Defence in depth: calling a service directly with forged headers           -> 401
curl -i localhost:8082/books -H "X-User-Id: 1" -H "X-User-Name: admin" -H "X-User-Role: ADMIN"
```

### 5. Load-balancing demo (2 x book-service, 2 x borrow-service)
Every instance registers in Eureka with a unique `instance-id` (`${spring.application.name}:${random.value}`).
The gateway routes with `lb://` through Spring Cloud LoadBalancer (round-robin), and borrow-service's Feign
clients are load-balanced too. Each response carries an **`X-Served-By: <service>:<port>`** header, and
`GET /books/instance`, `/borrows/instance` and `/fines/instance` return the instance that answered.

```bash
# bash: four terminals
cd book-service   && PORT=8082 ./mvnw spring-boot:run
cd book-service   && PORT=8092 ./mvnw spring-boot:run
cd borrow-service && PORT=8083 ./mvnw spring-boot:run
cd borrow-service && PORT=8093 ./mvnw spring-boot:run
# or with the packaged jar:  java -jar target/book-service-0.0.1-SNAPSHOT.jar --server.port=8092
# or:                        java -Dserver.port=8092 -jar target/book-service-0.0.1-SNAPSHOT.jar
```
```powershell
# PowerShell
cd book-service;   $env:PORT=8092; .\mvnw.cmd spring-boot:run
cd borrow-service; $env:PORT=8093; .\mvnw.cmd spring-boot:run
```
Wait about 10 seconds after the new instances register (Eureka and LoadBalancer caches refresh every 5 s), then:
```bash
for i in $(seq 1 6); do curl -s localhost:8080/books/instance   -H "Authorization: Bearer $USER_TOKEN"; echo; done
for i in $(seq 1 6); do curl -s localhost:8080/borrows/instance -H "Authorization: Bearer $USER_TOKEN"; echo; done
# or just the header:
for i in $(seq 1 6); do curl -s -o /dev/null -D - localhost:8080/books -H "Authorization: Bearer $USER_TOKEN" | grep -i x-served-by; done
```
Expected (ports alternate):
```
{"service":"book-service","instanceId":"book-service:3f9c...","host":"...","port":"8082"}
{"service":"book-service","instanceId":"book-service:a71e...","host":"...","port":"8092"}
{"service":"book-service","instanceId":"book-service:3f9c...","host":"...","port":"8082"}
...
```
```powershell
1..6 | % { (Invoke-WebRequest http://localhost:8080/books -Headers @{Authorization="Bearer $env:USER_TOKEN"}).Headers["X-Served-By"] }
```
Stop one instance: within about 15 s Eureka evicts it and all traffic moves to the remaining one.

### 6. Error format (every service)
```json
{"timestamp":"2026-10-30T16:40:00Z","status":404,"error":"Not Found","message":"Book not found with id 99","path":"/books/99"}
```
Validation errors add `"fieldErrors": {"field": "message"}`. Stack traces are never returned.

### 7. Health
`GET /actuator/health` and `GET /actuator/info` on every service (no token needed when called on the
service port). Eureka uses the actuator health for instance status (`eureka.client.healthcheck.enabled=true`).

### 8. Tests
`./mvnw test` in each project. The tests use H2 and `eureka.client.enabled=false`, so no PostgreSQL or Eureka is needed.
