# Handoff: Airports & Routes service

From the Flight Operations owner. This explains what is already in the skeleton, what Flight Operations needs from
your service, and how the requirements of P1 / PL2 / PL3 were solved on the Flight Operations side, so you can reuse
the code instead of starting from zero.

## 1. What the skeleton already has

- Spring Boot 3.3, Java 21, Maven wrapper, Dockerfile, port 8082
- JWT validation with the shared secret (`JWT_SECRET`), roles, `/internal/**` restricted to role `SERVICE`
- `AUDIT` log of every request, `GlobalExceptionHandler` (404 / 503)
- `internal/InternalAirportsRoutesController` + `AirportInfo` / `RouteInfo`, currently answering **501**
- `docs/service-contracts.md`: the agreement between the three services. Read it first.

Users log in on flight-operations-service (`POST /api/auth/login`). Your service only validates tokens, so there is
no user table here.

## 2. What Flight Operations needs from you (do this first)

Flight Operations calls two endpoints of yours (both need a SERVICE token):

```
GET /internal/routes/{routeId}      to validate a booking
GET /internal/airports/{iata}       to check the airport exists before listing its departures
```

```json
{
  "routeId": "route-opo-lis",
  "status": "ACTIVE",
  "distanceKm": 277.0,
  "minRangeRequired": 350.0,
  "minCapacityRequired": 100,
  "origin":      { "iataCode": "OPO", "status": "OPERATIONAL", "certifiedModels": ["A320neo", "737 MAX"] },
  "destination": { "iataCode": "LIS", "status": "OPERATIONAL", "certifiedModels": ["A320neo", "737 MAX"] }
}
```

`GET /internal/airports/{iata}` returns the same shape as `origin` above.

How each field is used when a flight is booked:

| Field | Rule in Flight Operations |
|---|---|
| `status` (route) | must be `ACTIVE` (`ACTIVE` or `DEACTIVATED`) |
| `origin.status`, `destination.status` | both must be `OPERATIONAL` (`OPERATIONAL`, `CLOSED`, `UNDER_MAINTENANCE`) |
| `certifiedModels` | both airports must list the aircraft's `modelName`. It is an exact string match with Aircraft & Maintenance (`A320neo`, `737 MAX`, ...) |
| `distanceKm` | the aircraft's max range must be >= it; also used for the fuel efficiency report |
| `minCapacityRequired` | the aircraft's seats must be >= it |

The route embeds both airports, so Flight Operations needs **one** call per booking. That means your route endpoint
has to fill in the airports, which may live on another instance of your service (see section 4).

**Status codes matter:**

- `200` with the JSON above.
- `404` **only when no instance of your service has it.** Flight Operations treats 404 as final and does not try your
  other instance. So an instance that doesn't hold the route/airport locally must ask its peer before answering 404
  (PL3 p.11). With a copy of each item on another instance, a stopped instance's data is still found there.
- Anything else (5xx, timeout, connection refused) makes Flight Operations retry on your other instance
  (round-robin with failover, then circuit breaker).
- `501` (what the skeleton returns now) shows up to users as `503 "... does not implement GET /internal/routes/...
  yet"`. That message disappears once the endpoints are implemented.

Extra JSON fields are ignored, so returning more than this is fine.

**Bootstrap data:** use the IDs in the contract doc so the services line up. Flight Operations' test data
(`stub` profile, Postman collection) also uses these:

| Route | Status | From → To | km | Min range | Min seats |
|---|---|---|---|---|---|
| `route-opo-lis` | ACTIVE | OPO → LIS | 277 | 350 | 100 |
| `route-lis-mad` | ACTIVE | LIS → MAD | 502 | 600 | 150 |
| `route-mad-opo` | ACTIVE | MAD → OPO | 420 | 500 | 120 |
| `route-opo-mad-old` | DEACTIVATED | OPO → MAD | 420 | 500 | 120 |

Airports `OPO`, `LIS`, `MAD`: `OPERATIONAL`, certified for `A320neo` and `737 MAX` only. 777X and A350 are left out
on purpose; the negative tests rely on that.

If you bootstrap the same data, the Postman collection of flight-operations-service also passes against the real
services, not only against its stub.

## 3. Porting from the monolith

From `pt.isep.psoft.alsafe.*`:

- `airportmanagement` (all of it)
- from `flightroutes`: `FlightRoute`, `RouteHistory`, `RouteId`, `RouteRequirement`, `RouteStatus`,
  `FlightRouteRepository`, `FlightRouteService`, `FlightRouteController` (+ assembler/DTOs), `services/routing/*`,
  `services/strategy/*`
- **not** `ScheduledFlight*`, `AircraftUtilization*`, `FuelEfficiency*`. Those are already in flight-operations-service.
- the airport/route parts of `bootstrap.Bootstrapper`

Then remove direct imports of other modules:

- Certifications reference the aircraft **model name** as a plain string, not an entity.
- Anything about scheduled flights (e.g. `/reports/utilization`) is now Flight Operations' job: call it or drop it.

Each service has its own database, and cross-references are by ID only.

## 4. Distribution: how Flight Operations did it (copy what you need)

The assignment asks for more than the week-3 practical session (where each item lives on one instance only): data
split between the instances **and** "redundancy-based fault tolerance" (P1 p.2). So: **2 instances per service, each
with its own database, the data split between them, and every item also kept on one other instance** (owner +
backup). When an instance is stopped, its data must still be readable from the other one. Changes reach the copy
shortly after (eventual consistency is fine: AP in CAP), and not every instance needs everything.

Paths below are in `flight-operations-service/src/main/java/pt/isep/sidis/flightops/`. The replication files are in
pull request #2 (branch `feature/replication`) until it is merged into `main`.

| Requirement | Flight Operations solution | Files to look at |
|---|---|---|
| Hardcoded peer list (PL3 p.12) | `flightops.cluster=instance1=url,instance2=url`, the same list on every instance | `cluster/Cluster.java`, `application-instance1.properties` |
| Who stores what (P1 p.15) | rendezvous hashing on the key: every instance computes the same ranking of the instances, with no coordination; the first is the owner, the second keeps a copy | `Cluster.ownerOf`, `Cluster.replicasOf` |
| Not found locally → ask peers (PL3 p.11) | public `/api/...` endpoint looks locally, then asks peers on `/internal/...` endpoints that **only** look locally (no forwarding loops) | `peers/PeerClient.java`, `api/InternalFlightController.java` |
| Peer down (PL3 p.12, p.14) | timeouts, retries with backoff for GETs, circuit breaker, health checks every 5 s | `resilience/*` |
| Copies (P1 p.2, redundancy) | a change and its "copy it to instance X" task are saved in one transaction (outbox), sent right after the commit and retried while X is down; every item has a `revision`, the newest copy wins | `replication/*`, `domain/ReplicationTask.java`, `revision` in `domain/ScheduledFlight.java` |
| Catch-up after a restart | at startup (before reporting ready) and every 60 s each instance fetches the items it should hold from its peers, so it recovers even with an empty database | `replication/ReplicaSync.java`, `api/InternalReplicaController.java` |
| Proving it | 3 real instances in one test: stop one, read its data from the copy, restart it empty, check it caught up | `ReplicationIntegrationTest` (in `src/test`) |
| Pooled HTTP client, TLS | Apache HttpClient 5 via `RestClient` | `clients/HttpClientFactory.java` (+ `httpclient5` in `pom.xml`) |
| Tracing (PL3 p.19) | `X-Request-Id` passed between services, `[instance] [requestId]` in every log line | `common/tracing/*`, `logging.pattern.console` |

What to shard by is your call. Two options:

- **Airports by IATA code, routes by their own ID.** Simple, but a route lookup may need its two airports from the
  other instance (one extra call).
- **Routes by origin IATA**, so a route and its origin airport always live together. Only the destination may be
  remote.

Either is fine as long as you can explain it at the assessment. Write the choice down in your architecture doc.
Whichever you choose, keep each item on its owner and one backup.

## 5. Security (P1 p.16)

- **TLS 1.3:** copy `src/main/resources/application-tls.properties` from flight ops and rename the alias to
  `airports`. The certificates are already generated by `flight-operations-service/scripts/generate-dev-certs.sh`
  (`certs/airports.p12`, valid for `airports`, `airports-1..3` and `localhost`).
- **Encryption at rest:** `common/crypto/*` (deterministic AES, so equality queries still work), used with
  `@Convert(converter = EncryptedString.class)` on the entity fields.
- **Faster JWT handling:** replace `JwtUtils` and `AuthTokenFilter` with the flight-ops versions. They build the parser
  once and cache service tokens; under load the skeleton version is noticeably slower.

## 6. Database and Docker

- PostgreSQL per instance: copy `application-postgres.properties` and the `x-flightops-db` block of
  `flight-operations-service/docker-compose.yml`.
- The whole system starts from `flight-operations-service`: `docker compose up --build` (your repo must be cloned next
  to it, in `../airports-routes-service`). It already starts `airports-1` (port 8082) and `airports-2` (port 8092),
  with `PEERS` pointing at each other.
- When you switch to TLS, tell me: the compose file and `AIRPORTS_ROUTES_SERVICE_URLS` need `https://` and the
  certificate mount (there is a comment in the compose file about this).

## 7. Checklist

- [ ] `GET /internal/routes/{id}` and `GET /internal/airports/{iata}`: 200 with the JSON above, 404 only after asking
      the peer
- [ ] Bootstrap the routes and airports from section 2
- [ ] Port `airportmanagement` and the route parts of `flightroutes`, with no imports of other modules
- [ ] 2 instances, data sharded, peers asked when data isn't local
- [ ] Each item kept on 2 instances (owner + backup): stop one instance → its data is still readable from the other,
      and it catches up when it restarts
- [ ] Timeouts, retries and circuit breaker on peer calls
- [ ] TLS 1.3 profile, encryption at rest
- [ ] PostgreSQL per instance
- [ ] Postman collection for your endpoints (PL3 p.16-17)
- [ ] Your part of the documentation (architecture, diagrams, load tests). See `flight-operations-service/docs/`
      for the format we used

**Quick integration test:** with your service on 8082, run `./scripts/run-local.sh --h2` in
flight-operations-service (real services, no Docker needed), log in with `POST /api/auth/login` as `atcc` / `atcc123`,
then `GET /api/scheduled-flights/departures/LIS`. A 200 means your airport endpoint works. Booking a flight also needs
Aircraft & Maintenance on 8081.
