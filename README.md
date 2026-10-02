# airports-routes-service

AISafe Flight Management System – **Airports & Routes** service (SIDIS 2026/27, Assignment 1).

Owns: airports, runways, airport certifications, flight routes and route history.
Contract with the other services: [docs/service-contracts.md](docs/service-contracts.md).

> **Skeleton.** Security (JWT, roles, `/internal/**` = service-only, audit log), error handling, Dockerfile and config
> are ready. The domain still has to be ported from the PSOFT monolith.

## Run

```bash
./mvnw spring-boot:run          # port 8082, H2 in memory
```

## TODO (owner)

1. Port from the monolith (`pt.isep.psoft.alsafe.*`):
   - `airportmanagement` (all of it)
   - from `flightroutes`: `FlightRoute`, `RouteHistory`, `RouteId`, `RouteRequirement`, `RouteStatus`,
     `FlightRouteRepository`, `FlightRouteService`, `FlightRouteController` (+ assembler/DTOs), `services/routing/*`,
     `services/strategy/*`
   - **not** `ScheduledFlight*`, `AircraftUtilization*`, `FuelEfficiency*` – those are already in flight-operations-service
   - the airport/route parts of `bootstrap.Bootstrapper` (use the fixed route IDs in the contract doc)
2. Remove direct imports of `aircraftmanagement` (e.g. certifications reference the aircraft **model name**,
   a plain string) and of scheduled flights (e.g. `/reports/utilization` → ask flight-operations-service).
3. Implement `internal/InternalAirportsRoutesController` (currently returns 501). Flight Operations depends on it.
4. Replication: store data sharded per replica, and make GETs ask the peers (`airports.peers`) when the data isn't
   local. See `flight-operations-service` (`peers/PeerClient`, `services/FlightQueryService`) for a working example.
5. Users log in on flight-operations-service. This service only **validates** tokens, so there is no user table here.
