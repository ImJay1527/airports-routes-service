# Airports & Routes Service – Architecture & Distributed Design Document
**Course**: Sistemas Distribuídos (LETI-SIDIS 2026/2027) – ISEP  
**Professor**: Orlando Sousa  
**Student**: Afonso Sobreiro (1241013)  
**Assigned Service**: `airports-routes-service`  
**Transversal Responsibility**: Component Cloning, P2P Instance Replication & Unicast Query Forwarding  

---

## 1. Executive Summary & System Decomposition

The AISafe monolithic air traffic safety and flight scheduling system has been decomposed into three autonomous, domain-aligned microservices communicating exclusively over HTTP/REST:

1. **`aircraft-maintenance-service`** (André - 1240567): Manages aircraft models, physical aircraft fleet status, and maintenance logs. Port `:8081` (replica `:8091`).
2. **`airports-routes-service`** (Afonso - 1241013): Manages airport infrastructure, runways, aircraft model certifications, and flight route topologies. Port `:8082` (replica `:8092`).
3. **`flight-operations-service`** (José - 1240624): Coordinates flight planning, crew assignment, and flight dispatching via synchronous command orchestration. Port `:8083` (replica `:8093`).

Each service operates under the **Database-per-Service** pattern with independent schemas (H2 in development, PostgreSQL in staging/production), preventing database coupling and enforcing strict bounded contexts.

---

## 2. Architectural Drivers: FURPS+ & ISO/IEC 25010 Quality Model

The architecture addresses functional and non-functional requirements categorized under **FURPS+** and mapped to the **ISO/IEC 25010** software quality standard:

| Quality Category | ISO 25010 Characteristic | Architectural Implementation & Quality Driver |
| :--- | :--- | :--- |
| **Functionality (F)** | Functional Suitability | CRUD for airports and flight routes; validation of aircraft model certifications; distance and flight time calculations; internal query contract for flight planners. |
| **Reliability (R)** | Fault Tolerance & Recoverability | **Component Cloning**: Multi-instance active replication (`airports-1` on `:8082`, `airports-2` on `:8092`). **Unicast Forwarding**: Queries forward across peers on local misses. Loop prevention via `X-Forwarded-From` stops infinite cycles. Circuit breaking and connect/read timeouts (1500ms) prevent cascading failures. |
| **Performance (P)** | Performance Efficiency | Local-first retrieval with zero network hops when data resides on the queried replica; synchronous HTTP/REST with low latency (<50ms for local queries, <150ms for forwarded queries); connection pooling via HikariCP. |
| **Security (S+)** | Security & Confidentiality | Stateless JWT-based authentication. Strict separation of public API (`/api/**` requiring user roles such as `ATCC` or `BACKOFFICE_OPERATOR`) and private internal API (`/internal/**` requiring `SERVICE` role). Shared cluster secret prevents unauthorized access. |
| **Supportability (S+)** | Maintainability & Modularity | Spring Boot 3 modular design; Clean Architecture separating domain entities, services, and presentation; Docker readiness; automated MockMvc unit and integration tests. |
| **Observability (+)** | Analysability | Distributed correlation IDs (`X-Request-Id`) propagated across all inter-service and peer requests, populated into SLF4J MDC; instance identification via `X-Instance`; data source tracking via `X-Data-Source: local|peer`. |

---

## 3. Key Architectural Decisions (ADRs)

### ADR-1: Commands Only (No Asynchronous Events)
* **Context**: While modern microservices often leverage asynchronous event brokers (e.g., Apache Kafka, RabbitMQ), Assignment 1 guidelines explicitly restrict system communication.
* **Decision**: All inter-service and inter-instance communications rely **exclusively on synchronous HTTP/REST commands**. No event queues, message brokers, or transactional outboxes are utilized.
* **Consequence**: Data consistency and inter-service coordination must be handled synchronously through resilient HTTP queries and command compensations rather than eventual event streaming.

### ADR-2: Component Cloning & Data Partitioning
* **Context**: The service must support high availability and horizontal scaling without a shared database bottleneck.
* **Decision**: Deploy multiple autonomous clones (`airports-1`, `airports-2`) each with its own persistent store. A shard or partition may reside natively on one clone or both.
* **Consequence**: Clones must be capable of collaborating to resolve queries for resources stored on peer replicas.

### ADR-3: Unicast Peer Forwarding with Loop Prevention
* **Context**: A client or caller (e.g., Flight Operations) may query any available instance clone. If the queried clone experiences a local data miss, it must determine whether another clone holds the resource.
* **Decision**: The queried clone executes a **Unicast HTTP GET** to its configured peer list (`airports.peers`).
  * If found on a peer, the result is returned to the client with `X-Data-Source: peer`.
  * To prevent ping-pong cycles and infinite loops, any forwarded request includes the header `X-Forwarded-From: <selfName>`. If an instance receives a request containing `X-Forwarded-From`, it only queries its local database and never forwards further.
  * If both local and peer lookups miss, a definitive `404 Not Found` is returned.

### ADR-4: CAP Theorem & Consistency Model (AP Alignment)
* **Context**: Network partitions can isolate service instances.
* **Decision**: We align with **AP (Availability and Partition Tolerance)**. Airport catalogs and route topologies are read-dominated and have low volatility. Serving available catalog data even under network degradation is preferred over blocking air traffic control operations.

---

## 4. C4 Architecture Diagrams

### 4.1. Level 2: Container Diagram (System Distributed Topology)

```mermaid
%% C4 Level 2 Container Diagram
flowchart LR
    client["Clients & Operators<br/>(ATCC, Backoffice Operator, Postman)"]
    lb["Load Balancer / Ingress<br/>https://localhost:8443"]

    subgraph AR["airports-routes-service (This Service)"]
        direction TB
        ar1["airports-1<br/>Port :8082"]
        ar2["airports-2<br/>Port :8092"]
        db1[("Database 1<br/>(H2 / PostgreSQL 5432)")]
        db2[("Database 2<br/>(H2 / PostgreSQL 5435)")]
        ar1 --- db1
        ar2 --- db2
        ar1 <-->|"P2P Unicast Forwarding<br/>GET /internal/** (SERVICE JWT)"| ar2
    end

    subgraph FO["flight-operations-service"]
        direction TB
        fo1["instance1 :8083"]
        fo2["instance2 :8093"]
        fo1 <--> fo2
    end

    subgraph AM["aircraft-maintenance-service"]
        direction TB
        am1["instance1 :8081"]
        am2["instance2 :8091"]
        am1 <--> am2
    end

    client -->|"HTTPS + User JWT<br/>(/api/airports, /api/flight-routes)"| lb
    lb --> ar1
    lb --> ar2

    FO -->|"Inter-Service Query<br/>GET /internal/routes/{id}<br/>GET /internal/airports/{iata}"| AR
    FO -->|"Inter-Service Query<br/>GET /internal/aircraft/{reg}"| AM

    classDef db fill:#eef6ff,stroke:#5b8def
    class db1,db2 db
```

---

### 4.2. Level 3: Component Diagram (Inside an `airports-routes-service` Instance)

```mermaid
%% C4 Level 3 Component Diagram
flowchart TB
    req(["HTTP Request"])

    subgraph Filters["Security & Tracing Pipeline"]
        direction LR
        f1["RequestTracingFilter<br/>X-Request-Id, X-Instance"] --> f2["AuthTokenFilter<br/>Validate JWT & Populate SecurityContext"] --> f3["AuditLogFilter<br/>Audit logging (user, uri, status)"]
    end

    subgraph Controllers["REST Controllers"]
        direction LR
        c1["AirportController<br/>/api/airports/**<br/>Roles: BACKOFFICE_OPERATOR"]
        c2["FlightRouteController<br/>/api/flight-routes/**<br/>Roles: ATCC, BACKOFFICE"]
        c3["InternalAirportsRoutesController<br/>/internal/**<br/>Role: SERVICE"]
    end

    subgraph Services["Domain Services & Peer Coordination"]
        direction LR
        s1["AirportService<br/>Airports, Runways, Certifications"]
        s2["FlightRouteService<br/>Routes, Distances, Strategy"]
        p["HttpPeerClient<br/>(implements PeerClient)"]
        cl["ClusterConfig<br/>Peer URLs & topology"]
    end

    subgraph Persistence["Persistence Layer"]
        direction LR
        repoA[("AirportRepository")]
        repoR[("FlightRouteRepository")]
        db[("Dedicated DB<br/>(H2 / PostgreSQL)")]
        repoA --- db
        repoR --- db
    end

    req --> Filters --> Controllers
    c1 --> s1
    c2 --> s2
    c3 --> repoA
    c3 --> repoR
    c3 --> p
    s1 --> repoA
    s2 --> repoR
    p --> cl
    p -->|"Unicast HTTP GET<br/>+ X-Forwarded-From<br/>+ SERVICE JWT"| peerOut(["Peer Replica Instance"])
```

---

## 5. C4+1 Process View Scenarios

### Scenario 1: Unicast Peer Forwarding on Local Miss (Component Cloning)

When `flight-operations-service` queries `airports-1` for airport `MAD`, but `MAD` is stored on clone `airports-2`:

```mermaid
%% Sequence Diagram: P2P Forwarding
sequenceDiagram
    autonumber
    participant FO as Flight Operations Service
    participant A1 as airports-1 (Port 8082)
    participant DB1 as Database 1 (Local Shard)
    participant A2 as airports-2 (Port 8092)
    participant DB2 as Database 2 (Remote Shard)

    FO->>A1: GET /internal/airports/MAD<br/>Authorization: Bearer SERVICE_JWT<br/>X-Request-Id: req-101
    A1->>DB1: findByIataCode_Code("MAD")
    DB1-->>A1: Optional.empty() (Local Miss)

    Note over A1: Local miss detected.<br/>Inspect X-Forwarded-From.<br/>Header not present -> Query peer clone.

    A1->>A2: GET /internal/airports/MAD<br/>Authorization: Bearer SERVICE_JWT<br/>X-Forwarded-From: airports-1<br/>X-Request-Id: req-101
    A2->>DB2: findByIataCode_Code("MAD")
    DB2-->>A2: Airport entity [MAD, OPERATIONAL, [A320neo, B737]]
    A2-->>A1: 200 OK AirportInfo JSON<br/>X-Data-Source: local<br/>X-Instance: airports-2
    
    Note over A1: Attach lineage header X-Data-Source: peer
    A1-->>FO: 200 OK AirportInfo JSON<br/>X-Data-Source: peer<br/>X-Instance: airports-1<br/>X-Request-Id: req-101
```

---

### Scenario 2: Replica Failure & Resilient Fallback

When a peer clone is offline or unreachable, the system fails gracefully without thread starvation or cascading failure:

```mermaid
%% Sequence Diagram: Fault Tolerance & Peer Unreachability
sequenceDiagram
    autonumber
    participant FO as Flight Operations Service
    participant A1 as airports-1 (Active Replica)
    participant DB1 as Database 1 (Local Shard)
    participant A2 as airports-2 (Crashing / Down Replica)

    FO->>A1: GET /internal/airports/FAO<br/>Authorization: Bearer SERVICE_JWT<br/>X-Request-Id: req-202
    A1->>DB1: findByIataCode_Code("FAO")
    DB1-->>A1: Optional.empty() (Local Miss)

    A1-xA2: GET /internal/airports/FAO (Peer Unreachable / Timeout)
    Note over A1: PeerClient catches timeout (1500ms)<br/>Logs warning with [req-202]<br/>Returns Optional.empty()

    A1-->>FO: 404 Not Found<br/>X-Instance: airports-1<br/>X-Request-Id: req-202
    Note over FO: Caller receives clear, timely 404.<br/>No hung threads or thread pool exhaustion.
```

---

### Scenario 3: Inter-Service Command Coordination (Flight Validation)

How `flight-operations-service` coordinates synchronous command checks across `airports-routes-service` and `aircraft-maintenance-service`:

```mermaid
%% Sequence Diagram: Inter-Service Command Coordination
sequenceDiagram
    autonumber
    participant Op as Flight Dispatcher
    participant FO as flight-operations-service
    participant AR as airports-routes-service
    participant AM as aircraft-maintenance-service

    Op->>FO: POST /api/flights (Create Flight Plan)<br/>Route: route-opo-lis, Aircraft: CS-TNM
    FO->>AR: GET /internal/routes/route-opo-lis (Verify Route & Distance)
    AR-->>FO: 200 OK [distance: 275km, requiredModel: A320neo]
    
    FO->>AM: GET /internal/aircraft/CS-TNM (Verify Airworthiness & Model)
    AM-->>FO: 200 OK [model: A320neo, status: AIRWORTHY]

    FO->>AR: GET /internal/airports/LIS (Verify Destination Airport Certification)
    AR-->>FO: 200 OK [status: OPERATIONAL, certifications: [A320neo, B737]]

    Note over FO: All pre-conditions verified synchronously.<br/>Dispatch flight.
    FO-->>Op: 201 Created [Flight #AF124 planned]
```

---

## 6. Verification & Automated Test Coverage

The solution is verified by a suite of automated unit and integration tests:

| Test Case | Objective | Verification Method |
| :--- | :--- | :--- |
| `testLocalHitDoesNotForwardToPeer` | Ensure local data is served directly with zero inter-instance overhead. | MockMvc GET `/internal/airports/LIS`. Asserts status 200, header `X-Data-Source: local`, and `verify(peerClient, never())`. |
| `testLocalMissForwardsToPeerAndReturnsAggregatedResult` | Validate unicast forwarding across cloned components when data resides on peer. | MockMvc GET `/internal/airports/FAO` with mocked peer hit. Asserts status 200, header `X-Data-Source: peer`. |
| `testLoopPreventionWhenAlreadyForwarded` | Prove that circular query loops cannot occur in multi-instance topologies. | MockMvc GET with header `X-Forwarded-From: airports-2`. Asserts status 404 and `verify(peerClient, never())`. |
| `testLocalMissAndPeerMissReturnsNotFound` | Confirm definitive 404 when no clone in the cluster holds the resource. | MockMvc GET `/internal/airports/NONEXISTENT`. Asserts status 404 after single peer query. |
| `testRouteForwardingWhenMissingLocally` | Validate unicast forwarding for flight routes. | MockMvc GET `/internal/routes/route-opo-fao`. Asserts status 200, header `X-Data-Source: peer`. |
| `testInternalForbiddenForNonServiceUser` | Enforce inter-service security boundary. | MockMvc GET with user token lacking `ROLE_SERVICE`. Asserts status 403 Forbidden. |
| `testInternalAllowedForServiceUser` | Verify authorized service queries succeed. | MockMvc GET with `ROLE_SERVICE`. Asserts status 200 OK. |
