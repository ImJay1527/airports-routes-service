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

## 4. C4+1 Architectural Views

The system architecture is structured according to Philippe Kruchten's **4+1 View Model** (IEEE Software, 1995) adapted for distributed systems using the **C4 hierarchical abstraction**:

```
                  +-----------------------------------+
                  |         4+1 View Model            |
                  +-----------------------------------+
                                    |
          +-------------------------+-------------------------+
          |                         |                         |
   [Logical View]            [Process View]            [Deployment View]
   C4 Level 2 Container      Dynamic Scenarios (+1)     Physical/Virtual Nodes
   C4 Level 3 Component      Forwarding & Sagas         Ports & Network IPs
```

---

### 4.1. View 1: Logical View

#### 4.1.1. C4 Level 2: Container Diagram (System Distributed Topology)
Visualizes system actors, boundary protocols, the 3 microservices with their cloned instances, and dedicated database containers.

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

#### 4.1.2. C4 Level 3: Component Diagram (Inside an `airports-routes-service` Instance)
Details the internal building blocks of an instance, illustrating the security/tracing filter chain, controllers, domain services, persistence repositories, and peer client.

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

### 4.2. View 2: Process View (The "+1" Dynamic Distributed Scenarios)

The "+1" scenarios make runtime interactions, failure recovery, and distributed coordination explicit.

#### Scenario 2.1: Request Routing & Unicast Peer Forwarding with Failure Handling
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
    DB2-->>A2: Airport entity [MAD, OPERATIONAL, [A320neo, 737 MAX]]
    A2-->>A1: 200 OK AirportInfo JSON<br/>X-Data-Source: local<br/>X-Instance: airports-2
    
    Note over A1: Attach lineage header X-Data-Source: peer
    A1-->>FO: 200 OK AirportInfo JSON<br/>X-Data-Source: peer<br/>X-Instance: airports-1<br/>X-Request-Id: req-101
```

#### Scenario 2.2: Node Failure & Resilient Recovery (Timeout, Circuit Breaking)
When a peer clone crashes or becomes unreachable, the calling instance protects its own thread pool and gracefully terminates the query:

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
    Note over A1: HttpPeerClient catches timeout (1500ms)<br/>Logs warning with [req-202]<br/>Returns Optional.empty()

    A1-->>FO: 404 Not Found<br/>X-Instance: airports-1<br/>X-Request-Id: req-202
    Note over FO: Caller receives clear, timely 404.<br/>No hung threads or thread pool exhaustion.
```

#### Scenario 2.3: Distributed Saga with Compensating Actions
Visualizes distributed transaction coordination across segregated services. When scheduling a flight, atomicity is maintained synchronously with compensating actions upon failure:

```mermaid
%% C4+1 Process View: Distributed Saga Pattern with Compensations
sequenceDiagram
    autonumber
    participant Op as Flight Dispatcher
    participant FO as flight-operations-service
    participant AR as airports-routes-service
    participant AM as aircraft-maintenance-service

    Note over Op,FO: Step 1: Initiate Booking Saga
    Op->>FO: POST /api/flights (Create Flight Plan)<br/>Route: route-opo-lis, Aircraft: CS-TNM
    
    Note over FO: Local Step: Reserve Provisional Flight Slot (State: PENDING)
    
    Note over FO,AR: Step 2: Validate Route & Airports
    FO->>AR: GET /internal/routes/route-opo-lis<br/>Authorization: Bearer SERVICE_JWT
    AR-->>FO: 200 OK [distance: 277km, status: ACTIVE, requiredModel: A320neo]
    
    FO->>AR: GET /internal/airports/LIS<br/>Authorization: Bearer SERVICE_JWT
    AR-->>FO: 200 OK [status: OPERATIONAL, certifications: [A320neo, 737 MAX]]

    Note over FO,AM: Step 3: Validate Aircraft Airworthiness
    FO->>AM: GET /internal/aircraft/CS-TNM<br/>Authorization: Bearer SERVICE_JWT
    
    alt Aircraft Maintenance Check Fails (Unavailable / Maintenance Required)
        AM-->>FO: 422 Unprocessable Entity [status: UNDER_MAINTENANCE]
        Note over FO: SAGA FAILURE TRIGGERED!<br/>Execute Compensating Transaction:
        Note over FO: Compensator C1: Cancel Provisional Reservation<br/>Update Flight State -> CANCELLED
        FO-->>Op: 422 Unprocessable Entity<br/>{"error": "Flight planning rejected: Aircraft CS-TNM under maintenance"}
    else Aircraft Airworthy & Validated
        AM-->>FO: 200 OK [model: A320neo, status: AIRWORTHY, maxRange: 6100km]
        Note over FO: All Distributed Invariants Satisfied.<br/>Commit Transaction: Update Flight State -> CONFIRMED
        FO-->>Op: 201 Created [Flight #AF124 Scheduled]
    end
```

---

### 4.3. View 3: Deployment View (Physical/Virtual Nodes & Network Topology)

Shows the deployment architecture across container environments, detailing reverse proxy ingress, container IP assignments on Docker bridge `172.20.0.0/16`, listening ports, and dedicated database volumes:

```mermaid
%% C4 Deployment View: Physical / Virtual Nodes & Network Topology
flowchart TB
    subgraph ExtNet["External Client Network"]
        user["Operator / ATCC Client<br/>Browser / Postman"]
    end

    subgraph Host["Docker Host / Virtual Machine Environment (172.20.0.0/16)"]
        subgraph IngressTier["Traffic Distribution Layer"]
            lb["Nginx Reverse Proxy / Load Balancer<br/>Container: aisafe-lb<br/>IP: 172.20.0.2<br/>Port: 8443 (HTTPS Ingress)"]
        end

        subgraph AppTier["Application Tier (Stateless Clones)"]
            subgraph ARCluster["airports-routes Cluster"]
                ar1["airports-1<br/>Container: airports-1<br/>IP: 172.20.0.10<br/>Port: 8082 (HTTP/REST)"]
                ar2["airports-2<br/>Container: airports-2<br/>IP: 172.20.0.11<br/>Port: 8092 (HTTP/REST)"]
            end
            subgraph FOCluster["flight-operations Cluster"]
                fo1["flightops-1<br/>IP: 172.20.0.20 :8083"]
                fo2["flightops-2<br/>IP: 172.20.0.21 :8093"]
            end
            subgraph AMCluster["aircraft-maintenance Cluster"]
                am1["aircraft-1<br/>IP: 172.20.0.30 :8081"]
                am2["aircraft-2<br/>IP: 172.20.0.31 :8091"]
            end
        end

        subgraph StorageTier["Persistence Tier (Dedicated Shards)"]
            db1[("Database 1<br/>Container: airports-db-1<br/>PostgreSQL 16 / H2<br/>IP: 172.20.0.50<br/>Port: 5432")]
            db2[("Database 2<br/>Container: airports-db-2<br/>PostgreSQL 16 / H2<br/>IP: 172.20.0.51<br/>Port: 5435")]
        end
    end

    user -->|"HTTPS / Port 8443"| lb
    lb -->|"Round Robin / Health Check<br/>Port 8082"| ar1
    lb -->|"Round Robin / Health Check<br/>Port 8092"| ar2
    ar1 <-->|"P2P Unicast Forwarding<br/>X-Forwarded-From / SERVICE JWT"| ar2
    ar1 ---|"JDBC Connection<br/>HikariCP Pool"| db1
    ar2 ---|"JDBC Connection<br/>HikariCP Pool"| db2
    fo1 -->|"Inter-Service Query :8082/:8092"| ARCluster
    fo2 -->|"Inter-Service Query :8082/:8092"| ARCluster
```

#### 4.3.2. Standalone Docker Compose Deployment (`airports-routes-service`)

Details container orchestration, port bindings, named persistent volumes, and healthcheck dependencies within the service's own Docker environment:

```mermaid
%% C4 Deployment View: Standalone airports-routes Docker Compose Deployment
flowchart TB
    subgraph Host["Docker Host (Host Network)"]
        client["Client / Postman / Flight Operations"]

        subgraph DockerBridge["Docker Network: default (Bridge 172.28.0.0/16)"]
            subgraph ServiceCluster["Application Tier (Spring Boot 3 / Temurin 21)"]
                ar1["Container: airports-1<br/>Port in container: 8082<br/>Host Port: 8082:8082<br/>Profile: instance1,postgres<br/>Healthcheck: GET /actuator/health"]
                ar2["Container: airports-2<br/>Port in container: 8082<br/>Host Port: 8092:8082<br/>Profile: instance2,postgres<br/>Healthcheck: GET /actuator/health"]
            end

            subgraph DatabaseTier["Database Tier (PostgreSQL 16 Alpine)"]
                db1[("Container: airports-db-1<br/>Container Port: 5432<br/>Host Port: 5432:5432<br/>Healthcheck: pg_isready")]
                db2[("Container: airports-db-2<br/>Container Port: 5432<br/>Host Port: 5435:5432<br/>Healthcheck: pg_isready")]
            end

            subgraph Volumes["Docker Named Volumes (Persistent Storage)"]
                vol1[("Volume: airports-db-1-data<br/>Mount: /var/lib/postgresql/data")]
                vol2[("Volume: airports-db-2-data<br/>Mount: /var/lib/postgresql/data")]
            end
        end
    end

    client -->|"HTTP GET :8082"| ar1
    client -->|"HTTP GET :8092"| ar2

    ar1 <-->|"P2P Unicast Forwarding<br/>http://airports-2:8082<br/>X-Forwarded-From / SERVICE JWT"| ar2

    ar1 -->|"JDBC: postgresql://airports-db-1:5432/airports<br/>condition: service_healthy"| db1
    ar2 -->|"JDBC: postgresql://airports-db-2:5432/airports<br/>condition: service_healthy"| db2

    db1 --- vol1
    db2 --- vol2

    classDef app fill:#e1f5fe,stroke:#0288d1,stroke-width:2px;
    classDef db fill:#e8f5e9,stroke:#388e3c,stroke-width:2px;
    classDef vol fill:#fff3e0,stroke:#f57c00,stroke-width:1px,stroke-dasharray: 5 5;
    class ar1,ar2 app;
    class db1,db2 db;
    class vol1,vol2 vol;
```

---

### 4.4. View 4: Implementation View (Core Code Snippets)

Critical patterns implemented in `airports-routes-service`:

#### Loop Prevention & Peer Forwarding (`InternalAirportsRoutesController.java`)
```java
@GetMapping("/airports/{iata}")
public ResponseEntity<AirportInfo> getAirport(
        @PathVariable String iata,
        @RequestHeader(value = "X-Forwarded-From", required = false) String forwardedFrom,
        @RequestHeader(value = "X-Request-Id", required = false) String requestId,
        HttpServletResponse response) {

    Optional<Airport> optAirport = airportRepository.findByIataCode_Code(iata.toUpperCase());
    if (optAirport.isPresent()) {
        response.setHeader("X-Data-Source", "local");
        return ResponseEntity.ok(toAirportInfo(optAirport.get()));
    }

    // Loop Prevention: If already forwarded by a peer clone, do NOT forward again
    if (forwardedFrom != null && !forwardedFrom.isBlank()) {
        return ResponseEntity.notFound().build();
    }

    // Unicast Forwarding: Query peer replicas
    Optional<AirportInfo> fromPeer = peerClient.fetchAirportFromPeers(iata.toUpperCase(), requestId);
    if (fromPeer.isPresent()) {
        response.setHeader("X-Data-Source", "peer");
        return ResponseEntity.ok(fromPeer.get());
    }

    return ResponseEntity.notFound().build();
}
```

#### Distributed Correlation & Tracing (`RequestTracingFilter.java`)
```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
    String requestId = request.getHeader("X-Request-Id");
    if (!StringUtils.hasText(requestId) || requestId.length() > 64) {
        requestId = UUID.randomUUID().toString().substring(0, 8);
    }
    MDC.put("requestId", requestId);
    MDC.put("instance", instanceName);
    response.setHeader("X-Request-Id", requestId);
    response.setHeader("X-Instance", instanceName);
    try {
        chain.doFilter(request, response);
    } finally {
        MDC.remove("requestId");
        MDC.remove("instance");
    }
}
```

---

## 5. Verification & Automated Test Coverage

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
