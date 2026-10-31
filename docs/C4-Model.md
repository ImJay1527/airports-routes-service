# AISafe Flight Management System - C4+1 Architectural Model
**Component**: `airports-routes-service`  
**Course**: Sistemas Distribuídos (LETI-SIDIS 2026/27) – ISEP  
**Author**: Afonso Sobreiro (1241013)  

**Contents:** [Level 1: Context](#level-1-system-context-diagram) · [UML Logical View](#uml-logical-view-level-1-deployment-independent-component-model) · [Level 2: Containers](#level-2-container-diagram-domain-driven-segregation) · [Level 3: Components](#level-3-component-diagram-inside-airports-routes-service) · [Deployment View](#level-4-deployment-view-multi-instance-docker-deployment) · [Dynamic Scenarios (+1)](#the-1-dynamic-distributed-behavior-sequence-diagrams)

---

## 1. Introduction & Notation

This document formalizes the architectural design of the **Airports & Routes Service** using the **C4 model** (Context, Containers, Components, Code) originated by Simon Brown, combined with the dynamic "+1" scenarios of Philippe Kruchten's **4+1 View Model** (IEEE Software, 1995).

---

## Level 1: System Context Diagram

Visualizes the high-level boundaries of the AISafe distributed system, human actors, and the operational responsibilities of the airports and flight navigation routing division.

```mermaid
%% C4 Level 1 - System Context
flowchart TB
    classDef person fill:#08427b,stroke:#052e56,color:#fff;
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff;

    atcc["Air Traffic Control Officer (ATCC)<br/>[Person]<br/>Inspects airport status, runway availability & routes"]:::person
    bo["Backoffice Operator<br/>[Person]<br/>Maintains airports, runways, model certifications & route catalogs"]:::person
    disp["Flight Operations Dispatcher<br/>[Person]<br/>Coordinates flights and queries route constraints"]:::person

    aisafe["AISafe Flight Management System<br/>[Distributed Software System]<br/>Coordinates flights, fleet availability, and airport routing"]:::system

    atcc -->|"HTTPS / REST + JWT<br/>Search airports & view route requirements"| aisafe
    bo -->|"HTTPS / REST + JWT<br/>Configure airports, runways & certifications"| aisafe
    disp -->|"HTTPS / REST + SERVICE JWT<br/>Verify airport readiness & route distance"| aisafe
```

---

## UML Logical View Level 1 (Deployment-Independent Component Model)

Following the formal specification in *Testing Component Collaboration, Cloning, UML Logical View Level 1* (Prof. Orlando Sousa, Week 4 Practical Session, Slides 14–15):

* **Components (`<<component>>`):** Captures high-level service boundaries and functional autonomy regardless of physical deployment.
* **Instances (`<<instance>>`):** Visualizes redundancy and active cloning (`airports-1`, `airports-2`, `airports-3`) to eliminate single points of failure.
* **Interfaces & Endpoints:** Explicitly represents provided interfaces (Lollipop / REST endpoints) and required interfaces (Socket / REST client dependencies).
* **Dependencies:** Directed dependencies illustrating inter-service collaboration and unicast peer forwarding.

```mermaid
%% UML Logical View Level 1 (Deployment-Independent Structural Model)
flowchart LR
    classDef comp fill:#1e40af,stroke:#1e3a8a,color:#fff;
    classDef inst fill:#3b82f6,stroke:#1d4ed8,color:#fff;
    classDef iface fill:#10b981,stroke:#047857,color:#fff;

    subgraph AR_Comp["<<component>> Airports & Routes Service"]
        direction TB
        subgraph AR_Clones["<<instance>> Active Clones (Redundancy)"]
            arInst1["<<instance>> airports-1"]:::inst
            arInst2["<<instance>> airports-2"]:::inst
            arInst3["<<instance>> airports-3"]:::inst
            arInst1 <-->|"P2P Forwarding (TLS 1.3)"| arInst2
            arInst2 <-->|"P2P Forwarding (TLS 1.3)"| arInst3
            arInst1 <-->|"P2P Forwarding (TLS 1.3)"| arInst3
        end

        iInternal["<<interface>> /internal/airports/{iata}<br/>/internal/routes/{id}<br/>Role: SERVICE"]:::iface
        iPublic["<<interface>> /api/airports/**<br/>/api/flight-routes/**<br/>Roles: ATCC, BACKOFFICE_OPERATOR"]:::iface

        AR_Clones --- iInternal
        AR_Clones --- iPublic
    end

    subgraph FO_Comp["<<component>> Flight Operations Service"]
        foInst["<<instance>> flightops-1"]:::comp
    end

    subgraph AM_Comp["<<component>> Aircraft & Maintenance Service"]
        amInst["<<instance>> aircraft-1"]:::comp
    end

    foInst -.->|"Required Interface: Route & Airport Verification<br/>HTTPS GET /internal/routes/{id}"| iInternal
    foInst -.->|"Required Interface: Airworthiness Query<br/>HTTPS GET /internal/aircraft/{reg}"| amInst

    class AR_Comp comp;
    class FO_Comp comp;
    class AM_Comp comp;
```

---

## Level 2: Container Diagram (Domain-Driven Segregation)

Illustrates the decomposition of the original PSOFT monolith into three autonomous, domain-aligned microservices, highlighting active component cloning, isolated databases, and TLS 1.3 encryption in transit.

```mermaid
%% C4 Level 2 - Container Diagram
flowchart TB
    classDef person fill:#08427b,stroke:#052e56,color:#fff;
    classDef container fill:#438dd5,stroke:#2e6295,color:#fff;
    classDef db fill:#2e6295,stroke:#11365c,color:#fff,shape:cylinder;

    Users[("ATCC Officers & Backoffice Operators<br/>[Person]")]:::person

    subgraph AISafe["AISafe Distributed Architecture (Zero SPOF & Database-per-Replica)"]
        subgraph AR_Cluster["airports-routes-service (This Component)"]
            ar1["airports-1<br/>[Container: Spring Boot 3]<br/>Port :8082"]:::container
            ar2["airports-2<br/>[Container: Spring Boot 3]<br/>Port :8092"]:::container
            ar3["airports-3<br/>[Container: Spring Boot 3]<br/>Port :8102"]:::container
            db_ar1[("airports-db-1<br/>[PostgreSQL 16 / H2]<br/>Port :5432")]:::db
            db_ar2[("airports-db-2<br/>[PostgreSQL 16 / H2]<br/>Port :5435")]:::db
            db_ar3[("airports-db-3<br/>[PostgreSQL 16 / H2]<br/>Port :5436")]:::db
            ar1 --- db_ar1
            ar2 --- db_ar2
            ar3 --- db_ar3
            ar1 <-->|"P2P Forwarding (TLS 1.3)<br/>(SERVICE JWT, X-Forwarded-From)"| ar2
            ar2 <-->|"P2P Forwarding (TLS 1.3)"| ar3
            ar1 <-->|"P2P Forwarding (TLS 1.3)"| ar3
        end

        subgraph FO_Cluster["flight-operations-service"]
            fo1["flightops-1<br/>[Container: Spring Boot 3]<br/>Port :8083"]:::container
            fo2["flightops-2<br/>[Container: Spring Boot 3]<br/>Port :8093"]:::container
            db_fo[("flightops-db<br/>[PostgreSQL 16 / H2]")]:::db
            fo1 --- db_fo
            fo2 --- db_fo
        end

        subgraph AM_Cluster["aircraft-maintenance-service"]
            am1["aircraft-1<br/>[Container: Spring Boot 3]<br/>Port :8081"]:::container
            am2["aircraft-2<br/>[Container: Spring Boot 3]<br/>Port :8091"]:::container
            db_am[("aircraft-db<br/>[PostgreSQL 16 / H2]")]:::db
            am1 --- db_am
            am2 --- db_am
        end
    end

    Users -->|"HTTPS / REST + User JWT"| ar1
    Users -->|"HTTPS / REST + User JWT"| fo1
    Users -->|"HTTPS / REST + User JWT"| am1

    fo1 -->|"Inter-Service Query (TLS 1.3)<br/>GET /internal/airports/{iata}<br/>GET /internal/routes/{id}<br/>(Role: SERVICE)"| ar1
    fo1 -->|"Inter-Service Query (TLS 1.3)<br/>GET /internal/aircraft/{reg}<br/>(Role: SERVICE)"| am1
```

---

## Level 3: Component Diagram (Inside an `airports-routes-service` Instance)

Details the internal components of an instance, including the security and distributed tracing filter chain, REST presentation controllers, domain business logic, P2P peer client, and data persistence repositories.

```mermaid
%% C4 Level 3 - Component Diagram
flowchart TB
    classDef filter fill:#d97706,stroke:#92400e,color:#fff;
    classDef ctrl fill:#1168bd,stroke:#0b4884,color:#fff;
    classDef svc fill:#2563eb,stroke:#1d4ed8,color:#fff;
    classDef repo fill:#475569,stroke:#1e293b,color:#fff;
    classDef db fill:#1e3a8a,stroke:#172554,color:#fff,shape:cylinder;

    dbEngine[("Isolated DB<br/>[H2 / PostgreSQL 16]")]:::db

    subgraph Instance["airports-routes-service Instance"]
        subgraph Pipeline["Filter Chain (Ordered)"]
            direction LR
            fTrace["RequestTracingFilter<br/>(Order: HIGHEST)<br/>X-Request-Id, X-Instance, MDC"]:::filter
            fAuth["AuthTokenFilter<br/>JWT parsing & validation"]:::filter
            fAudit["AuditLogFilter<br/>Non-repudiation audit logging"]:::filter
            fTrace --> fAuth --> fAudit
        end

        subgraph Controllers["REST Presentation Layer"]
            direction TB
            cInternal["InternalAirportsRoutesController<br/>Handles /internal/airports/{iata} & /internal/routes/{id}<br/>Enforces SERVICE role & P2P forwarding"]:::ctrl
            cAirport["AirportController<br/>Handles /api/airports/**<br/>Roles: ATCC, BACKOFFICE_OPERATOR"]:::ctrl
            cRoute["FlightRouteController<br/>Handles /api/flight-routes/**<br/>Roles: ATCC, BACKOFFICE_OPERATOR"]:::ctrl
        end

        subgraph Core["Domain Services & Peer Coordination"]
            direction TB
            sAirport["AirportService<br/>Airport lifecycle, runway management & model certifications"]:::svc
            sRoute["FlightRouteService<br/>Route topology, distance calculation & runway constraints"]:::svc
            sPeer["HttpPeerClient<br/>Unicast HTTPS GET with TLS 1.3 & timeouts"]:::svc
            cfgCluster["ClusterConfig<br/>Self identity & Peer URLs"]:::svc
        end

        subgraph Repos["Data Access Layer (Spring Data JPA)"]
            direction LR
            rAirport[("AirportRepository")]:::repo
            rRoute[("FlightRouteRepository")]:::repo
        end
    end

    Pipeline --> Controllers

    cInternal --> rAirport
    cInternal --> rRoute
    cInternal --> sPeer
    sPeer --> cfgCluster
    sPeer -->|"Unicast HTTPS GET (TLS 1.3)<br/>X-Forwarded-From / SERVICE JWT"| extPeer(["Peer Replica Instance"])

    cAirport --> sAirport
    cRoute --> sRoute

    sAirport --> rAirport
    sRoute --> rRoute
    sRoute --> rAirport

    rAirport --- dbEngine
    rRoute --- dbEngine
```

---

## Level 4: Deployment Diagram (Physical & Virtual Container Topology)

Details the physical/virtual deployment architecture within Docker, illustrating listening ports, network aliases, dedicated storage volumes, and TLS certificates.

```mermaid
%% C4 Level 4 - Deployment Diagram
flowchart TB
    classDef client fill:#334155,stroke:#1e293b,color:#fff;
    classDef app fill:#0284c7,stroke:#0369a1,color:#fff;
    classDef db fill:#16a34a,stroke:#15803d,color:#fff;
    classDef vol fill:#ea580c,stroke:#c2410c,color:#fff,stroke-dasharray: 5 5;

    clientNode["Client Application / Postman / Flight Operations"]:::client

    subgraph DockerHost["Docker Host Environment (Linux Bridge 172.20.0.0/16)"]
        subgraph AppCluster["Application Tier (Eclipse Temurin 21)"]
            inst1["Container: airports-1<br/>Port: 8082<br/>Exposed: 8082:8082<br/>Profile: instance1,tls,postgres"]:::app
            inst2["Container: airports-2<br/>Port: 8082<br/>Exposed: 8092:8082<br/>Profile: instance2,tls,postgres"]:::app
            inst3["Container: airports-3<br/>Port: 8082<br/>Exposed: 8102:8082<br/>Profile: instance3,tls,postgres"]:::app
        end

        subgraph DBCluster["Persistence Tier (PostgreSQL 16 Alpine)"]
            db1[("Container: airports-db-1<br/>Port: 5432<br/>Exposed: 5432:5432<br/>Healthcheck: pg_isready")]:::db
            db2[("Container: airports-db-2<br/>Port: 5432<br/>Exposed: 5435:5432<br/>Healthcheck: pg_isready")]:::db
            db3[("Container: airports-db-3<br/>Port: 5432<br/>Exposed: 5436:5432<br/>Healthcheck: pg_isready")]:::db
        end

        subgraph VolumeTier["Persistent Named Volumes"]
            v1[("Volume: airports-db-1-data<br/>Mount: /var/lib/postgresql/data")]:::vol
            v2[("Volume: airports-db-2-data<br/>Mount: /var/lib/postgresql/data")]:::vol
            v3[("Volume: airports-db-3-data<br/>Mount: /var/lib/postgresql/data")]:::vol
        end

        subgraph CertsVolume["PKI Shared Certificates"]
            vc[("Volume: ./certs<br/>Mount: /certs:ro")]:::vol
        end
    end

    clientNode -->|"HTTPS GET :8082"| inst1
    clientNode -->|"HTTPS GET :8092"| inst2
    clientNode -->|"HTTPS GET :8102"| inst3

    inst1 <-->|"P2P Forwarding (TLS 1.3)"| inst2
    inst2 <-->|"P2P Forwarding (TLS 1.3)"| inst3
    inst1 <-->|"P2P Forwarding (TLS 1.3)"| inst3

    inst1 -->|"JDBC: postgresql://airports-db-1:5432/airports"| db1
    inst2 -->|"JDBC: postgresql://airports-db-2:5432/airports"| db2
    inst3 -->|"JDBC: postgresql://airports-db-3:5432/airports"| db3

    db1 --- v1
    db2 --- v2
    db3 --- v3

    inst1 --- vc
    inst2 --- vc
    inst3 --- vc
```

---

## The "+1": Dynamic Distributed Behavior (Sequence Diagrams)

### Scenario 1: P2P Unicast Query Forwarding Across Replicas
Demonstrates how Instance 1 satisfies a query for partition `FAO` (residing natively on Instance 2) while attaching correlation and provenance headers over TLS 1.3.

```mermaid
%% Sequence Diagram: P2P Query Forwarding
sequenceDiagram
    autonumber
    participant Caller as Calling Client (Flight Operations / Postman)
    participant AR1 as airports-1 (Port 8082)
    participant DB1 as Database 1 (Local Shard)
    participant AR2 as airports-2 (Port 8092)
    participant DB2 as Database 2 (Remote Shard)

    Caller->>AR1: GET /internal/airports/FAO<br/>Authorization: Bearer <SERVICE JWT><br/>X-Request-Id: req-101
    
    Note over AR1: 1. RequestTracingFilter sets MDC & X-Instance: instance1
    Note over AR1: 2. Query local database
    AR1->>DB1: findByIataCode_Code("FAO")
    DB1-->>AR1: Optional.empty() (Local Miss)

    Note over AR1: 3. Verify X-Forwarded-From is absent.<br/>Invoke HttpPeerClient.fetchAirportFromPeers()
    AR1->>AR2: GET /internal/airports/FAO (TLS 1.3)<br/>Authorization: Bearer <SERVICE JWT><br/>X-Forwarded-From: instance1<br/>X-Request-Id: req-101

    Note over AR2: 4. AR2 checks local database
    AR2->>DB2: findByIataCode_Code("FAO")
    DB2-->>AR2: Airport(FAO, Faro, Gago Coutinho, runways=[...])
    AR2-->>AR1: 200 OK + AirportInfo JSON<br/>X-Data-Source: local<br/>X-Instance: instance2

    Note over AR1: 5. Attach provenance header X-Data-Source: peer
    AR1-->>Caller: 200 OK + AirportInfo JSON<br/>X-Data-Source: peer<br/>X-Instance: instance1<br/>X-Request-Id: req-101
```

---

### Scenario 2: Distributed Loop Prevention (Cutting Cycle Storms)
Proves that recursive or circular forwarding requests are terminated at the first hop ($hop \le 1$).

```mermaid
%% Sequence Diagram: Loop Prevention
sequenceDiagram
    autonumber
    participant AR2 as airports-2 (Calling Peer)
    participant AR1 as airports-1 (Receiving Peer)
    participant DB1 as Database 1 (Local Shard)

    Note over AR2,AR1: AR2 forwards a request for non-existent airport "NONEXISTENT"
    AR2->>AR1: GET /internal/airports/NONEXISTENT (TLS 1.3)<br/>Authorization: Bearer <SERVICE JWT><br/>X-Forwarded-From: instance2<br/>X-Request-Id: req-202

    AR1->>DB1: findByIataCode_Code("NONEXISTENT")
    DB1-->>AR1: Optional.empty() (Local Miss)

    Note over AR1: Loop Prevention Check:<br/>X-Forwarded-From header is present ("instance2").<br/>CRITICAL: Do NOT query any peer. Stop propagation immediately.

    AR1-->>AR2: 404 Not Found<br/>X-Instance: instance1<br/>X-Request-Id: req-202
```

---

### Scenario 3: Inter-Service Flight Booking Saga with Compensating Actions
Illustrates the synchronous command sequence executed by Flight Operations to verify and book a flight, interacting with `airports-routes-service` and `aircraft-maintenance-service`.

```mermaid
%% Sequence Diagram: Inter-Service Saga
sequenceDiagram
    autonumber
    actor Dispatcher as ATCC Dispatcher
    participant FO as flight-operations-service
    participant AR as airports-routes-service
    participant AM as aircraft-maintenance-service

    Dispatcher->>FO: POST /api/flights (routeId, aircraftReg, departureTime)
    activate FO
    
    Note over FO: Step 1: Validate Route & Airport Feasibility
    FO->>AR: GET /internal/routes/{routeId} (TLS 1.3)
    activate AR
    AR-->>FO: 200 OK (RouteInfo: origin=OPO, dest=LIS, distance=277km)
    deactivate AR

    Note over FO: Step 2: Validate Aircraft Availability & Capacity
    FO->>AM: GET /internal/aircraft/{reg} (TLS 1.3)
    activate AM
    AM-->>FO: 200 OK (AircraftInfo: status=AVAILABLE, range=6300km)
    deactivate AM

    Note over FO: Step 3: Local Flight Registration
    FO->>FO: Create ScheduledFlight entity in local DB
    
    alt All preconditions met
        FO-->>Dispatcher: 201 Created (Flight Details)
    else Precondition fails (e.g. aircraft under maintenance)
        FO-->>Dispatcher: 409 Conflict / 400 Bad Request
    end
    deactivate FO
```
