# Airports & Routes Service – Architecture & Distributed Design

This document details the architectural decisions, C4 model representations, consistency model, and fault-tolerance mechanisms for the **Airports & Routes service** in the distributed AISafe system (SIDIS 2026/2027).

---

## 1. C4 Architecture Diagrams

### 1.1. Level 2: Container Diagram (Distributed System Overview)

The system is decomposed into 3 independent microservices communicating over HTTPS/REST with stateless JWT authentication. Each service runs in multiple replicas with its own dedicated database.

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
        ar1 <-->|"P2P Peer Forwarding<br/>GET /internal/** (SERVICE JWT)"| ar2
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

### 1.2. Level 3: Component Diagram (Inside an `airports-routes-service` Instance)

```mermaid
%% C4 Level 3 Component Diagram
flowchart TB
    req(["HTTP Request"])

    subgraph F["Security & Tracing Filters"]
        direction LR
        f1["RequestTracingFilter<br/>X-Request-Id, X-Instance"] --> f2["AuthTokenFilter<br/>Validate JWT & Extract Roles"] --> f3["AuditLogFilter<br/>Audit logging (user, uri, status)"]
    end

    subgraph C["Controllers"]
        direction LR
        c1["AirportController<br/>/api/airports/**<br/>Roles: BACKOFFICE_OPERATOR"]
        c2["FlightRouteController<br/>/api/flight-routes/**<br/>Roles: ATCC, BACKOFFICE"]
        c3["InternalAirportsRoutesController<br/>/internal/**<br/>Role: SERVICE"]
    end

    subgraph S["Domain Services & Clustering"]
        direction LR
        s1["AirportService<br/>Airports, Runways, Certifications"]
        s2["FlightRouteService<br/>Routes, Distances, Strategies"]
        p["PeerClient<br/>Queries peer replicas for missing shards"]
        cl["Cluster<br/>Peer URLs & topology"]
    end

    subgraph R["Resilience Engine"]
        direction LR
        r1["ResilientCaller<br/>Retries + Exponential Backoff"] --> r2["EndpointHealth<br/>Circuit Breaker"]
    end

    subgraph P["Persistence Layer"]
        direction LR
        repoA[("AirportRepository")]
        repoR[("FlightRouteRepository")]
        db[("Dedicated DB<br/>(H2 / PostgreSQL)")]
        repoA --- db
        repoR --- db
    end

    req --> F --> C
    c1 --> s1
    c2 --> s2
    c3 --> s1
    c3 --> s2
    c3 --> p
    s1 --> repoA
    s2 --> repoR
    s1 --> p
    s2 --> p
    p --> cl
    p --> R
    R -->|"HTTPS + SERVICE JWT<br/>Forward to peer replica"| peerOut(["Peer Replica Instance"])
```

---

## 2. Dynamic Interactions & P2P Replication

### 2.1. Peer-to-Peer Query Forwarding (Local Miss $\rightarrow$ Peer Forward)

When an incoming request reaches an instance that does not store the requested entity locally due to data partitioning:
1. The instance checks its local database.
2. On a local miss, it checks if the request was already forwarded. If not, it forwards the query to its peer replica via HTTP/REST using a `SERVICE` token and attaches `X-Forwarded-From: <instance-id>` to prevent recursion loops.
3. The peer replica looks up the record locally and returns it.
4. The initial instance returns the response to the caller with header `X-Data-Source: peer:<peer-id>`.
5. Only if **no** replica holds the resource is a `404 Not Found` returned, ensuring callers (such as Flight Operations) can rely on a 404 as final.

```mermaid
%% Sequence Diagram: P2P Forwarding
sequenceDiagram
    autonumber
    participant C as Caller (Flight Ops / Client)
    participant A1 as airports-1 (Port 8082)
    participant DB1 as Database 1 (Local Shard)
    participant A2 as airports-2 (Port 8092)
    participant DB2 as Database 2 (Remote Shard)

    C->>A1: GET /internal/airports/MAD<br/>Authorization: Bearer SERVICE_JWT, X-Request-Id: req-101
    A1->>DB1: findByIataCode("MAD")
    DB1-->>A1: Not Found (Local Shard Miss)

    Note over A1: Data not held locally.<br/>Forward query to peer replica<br/>with loop-prevention header.

    A1->>A2: GET /internal/airports/MAD<br/>X-Forwarded-From: airports-1, X-Request-Id: req-101
    A2->>DB2: findByIataCode("MAD") (Local check only)
    DB2-->>A2: Airport MAD (Found!)
    A2-->>A1: 200 OK { iataCode: "MAD", status: "OPERATIONAL", ... }
    
    A1-->>C: 200 OK { iataCode: "MAD", ... }<br/>X-Data-Source: peer:airports-2

    Note over A1,A2: Single distributed trace preserved across both instances via [req-101]
```

---

### 2.2. Failure & Automatic Recovery

```mermaid
%% Sequence Diagram: Fault Tolerance
sequenceDiagram
    participant C as Caller (Flight Ops / Client)
    participant A1 as airports-1 (Active Replica)
    participant A2 as airports-2 (Failing / Recovering Replica)

    Note over A2: airports-2 crashes / becomes unreachable
    C->>A1: GET /internal/airports/OPO
    Note over A1: OPO is stored locally on airports-1 (owner / backup copy)
    A1-->>C: 200 OK (Zero downtime for caller)

    C->>A1: GET /internal/airports/MAD (Owned by airports-2)
    A1-xA2: Attempt 1: Connection Refused (timeout)
    Note over A1: Retry with exponential backoff
    A1-xA2: Attempts 2 & 3: Failures threshold exceeded
    Note over A1: Circuit Breaker opens (state: OPEN for 10s)
    
    Note over A1: Falls back to backup copy if replicated, or returns graceful 503 / 404

    Note over A2: airports-2 process restarts
    A1->>A2: Periodic Health Probe GET /actuator/health
    A2-->>A1: 200 UP (Health restored)
    Note over A1: Circuit Breaker resets (state: CLOSED)
```

---

## 3. Consistency Model & CAP Theorem Justification

* **AP Alignment (Availability & Partition Tolerance)**:
  In accordance with the assignment guidelines, the system chooses **AP** over strict consistency (CP). Aviation infrastructure catalog data (airports and predefined flight routes) is read-heavy and exhibits low volatility. Prioritizing high availability and partition tolerance ensures flight planning services never stall because of transient network partitions.
* **Eventual Consistency**:
  Updates are replicated asynchronously between owner and backup instances. Replicas synchronize revisions to guarantee convergence across all active instances.

---

## 4. Security & Auditability

* **Inter-Service Authentication**:
  All service-to-service endpoints (`/internal/**`) strictly enforce `hasRole('SERVICE')`. Requests must present a signed HS256 JWT issued with the shared cluster secret. Public endpoints reject service tokens, preventing privilege escalations.
* **Audit Logging**:
  Every request entering an instance passes through `AuditLogFilter`, recording `user`, `roles`, `method`, `uri`, `status`, and processing time.
* **Distributed Tracing**:
  `X-Request-Id` is propagated across service-to-service and peer-to-peer calls, allowing unified distributed log correlation across replicas.
