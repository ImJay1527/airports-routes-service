# Airports & Routes Service

AISafe Flight Management System – **Airports & Routes** service (SIDIS 2026/2027, Assignment 1).  
**Student**: Afonso Sobreiro (1241013)  
**Transversal Topic**: Component Cloning & P2P Unicast Query Forwarding  

---

## 1. Responsibilities

- **Domain**: Airports, Runways, Aircraft Model Certifications, Flight Routes, and Routing Strategies.
- **Inter-service Contract**: [`docs/service-contracts.md`](docs/service-contracts.md) (`GET /internal/airports/{iata}` and `GET /internal/routes/{id}`).
- **Component Cloning**: Multi-instance active replication (`airports-1` on `:8082`, `airports-2` on `:8092`) with Unicast query forwarding and cycle prevention (`X-Forwarded-From`).
- **C4+1 Architecture**: Fully documented in [`docs/architecture.md`](docs/architecture.md).

---

## 2. Running the Service

### Option A: Local Multi-Instance Runner (Fastest, no Docker needed)
```bash
./scripts/run-local.sh            # Starts instance 1 (:8082) and instance 2 (:8092) with in-memory H2
./scripts/run-local.sh --tls      # Starts instances over HTTPS strictly enforcing TLS 1.3
./scripts/run-local.sh -n 3       # Starts 3 instances (:8082, :8092, :8102)
```

### Option B: Docker Compose (PostgreSQL per instance)
```bash
docker compose up --build         # Starts 2 instances with dedicated PostgreSQL databases
./scripts/run-docker.sh           # Or use the helper script
./scripts/run-docker.sh down      # Stop containers
./scripts/run-docker.sh --h2      # Lightweight mode with H2 (no postgres containers)
```

### Option C: Single Instance via Maven
```bash
export JAVA_HOME="/home/afonso/Downloads/idea-IU-262.10968.63/jbr" && export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run -Dspring-boot.run.profiles=instance1
# Or with TLS 1.3:
./mvnw spring-boot:run -Dspring-boot.run.profiles=instance1,tls
```

---

## 3. Testing & Verification

- **Automated Tests (12/12 passing)**:
  ```bash
  ./mvnw test
  ```
  Includes unit tests, domain tests, peer forwarding mock tests, and `TlsConfigurationIntegrationTest` validating TLS 1.3 protocol enforcement and mutual Dev CA trust.

- **Live Cluster Demonstration Script**:
  ```bash
  ./scripts/demo-cluster.sh         # Run 7 live distributed scenarios against HTTP
  ./scripts/demo-cluster.sh --tls   # Run 7 live distributed scenarios against HTTPS (TLS 1.3)
  ```

- **Postman Test Suite (28/28 assertions passing)**:
  - Collection: `postman/airports-routes.postman_collection.json`
  - Environment (HTTP): `postman/local.postman_environment.json`
  - Environment (HTTPS): `postman/local-https.postman_environment.json`
  - Tests local hits, peer forwarding, loop prevention, TLS handshakes, and role-based security boundaries.
