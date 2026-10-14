package pt.isep.sidis.airports.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.airports.airportmanagement.domain.*;
import pt.isep.sidis.airports.airportmanagement.repositories.AirportRepository;
import pt.isep.sidis.airports.cluster.ClusterConfig;
import pt.isep.sidis.airports.flightroutes.domain.*;
import pt.isep.sidis.airports.flightroutes.repositories.FlightRouteRepository;

@Component
public class Bootstrapper implements CommandLineRunner {

    private final AirportRepository airportRepository;
    private final FlightRouteRepository flightRouteRepository;
    private final ClusterConfig clusterConfig;

    public Bootstrapper(AirportRepository airportRepository,
                        FlightRouteRepository flightRouteRepository,
                        ClusterConfig clusterConfig) {
        this.airportRepository = airportRepository;
        this.flightRouteRepository = flightRouteRepository;
        this.clusterConfig = clusterConfig;
    }

    @Override
    @Transactional
    public void run(String... args) {
        bootstrapAirports();
        bootstrapFlightRoutes();
    }

    private void bootstrapAirports() {
        if (airportRepository.count() == 0) {
            // Core common catalog across all instances
            Location locOpo = new Location("Norte", "Portugal", "Porto", new GPSCoordinates(41.2481, -8.6814));
            Airport opo = new Airport(new IATACode("OPO"), "Francisco Sá Carneiro", locOpo, new Timezone("UTC+01:00"));
            opo.addCertification("A320neo");
            opo.addCertification("737 MAX");
            airportRepository.save(opo);

            Location locLis = new Location("Centro", "Portugal", "Lisboa", new GPSCoordinates(38.7742, -9.1342));
            Airport lis = new Airport(new IATACode("LIS"), "Humberto Delgado", locLis, new Timezone("UTC+01:00"));
            lis.addCertification("A320neo");
            lis.addCertification("737 MAX");
            airportRepository.save(lis);

            Location locMad = new Location("Madrid", "Espanha", "Madrid", new GPSCoordinates(40.4719, -3.5626));
            Airport mad = new Airport(new IATACode("MAD"), "Barajas", locMad, new Timezone("UTC+02:00"));
            mad.addCertification("A320neo");
            mad.addCertification("737 MAX");
            airportRepository.save(mad);

            // Sharded / partition-specific data to demonstrate P2P forwarding
            String self = clusterConfig.getSelfName();
            if (self != null && (self.contains("1") || self.equalsIgnoreCase("default"))) {
                Location locBcn = new Location("Catalunya", "Espanha", "Barcelona", new GPSCoordinates(41.2974, 2.0833));
                Airport bcn = new Airport(new IATACode("BCN"), "El Prat", locBcn, new Timezone("UTC+02:00"));
                bcn.addCertification("A320neo");
                bcn.addCertification("737 MAX");
                airportRepository.save(bcn);
            }

            if (self != null && self.contains("2")) {
                Location locFao = new Location("Algarve", "Portugal", "Faro", new GPSCoordinates(37.0144, -7.9659));
                Airport fao = new Airport(new IATACode("FAO"), "Gago Coutinho", locFao, new Timezone("UTC+01:00"));
                fao.addCertification("A320neo");
                fao.addCertification("737 MAX");
                airportRepository.save(fao);
            }

            System.out.println(" -> Bootstrapped airports for [" + clusterConfig.getSelfName() + "]");
        }
    }

    private void bootstrapFlightRoutes() {
        if (flightRouteRepository.count() == 0) {
            Airport opo = airportRepository.findByIataCode_Code("OPO").orElseThrow();
            Airport lis = airportRepository.findByIataCode_Code("LIS").orElseThrow();
            Airport mad = airportRepository.findByIataCode_Code("MAD").orElseThrow();

            // route-opo-lis: ACTIVE, 277 km, min range 350, min seats 100
            RouteRequirement req1 = new RouteRequirement(350.0, 100);
            FlightRoute route1 = new FlightRoute("route-opo-lis", opo, lis, 277.0, 45, req1, "Bootstrapper");
            flightRouteRepository.save(route1);

            // route-lis-mad: ACTIVE, 502 km, min range 600, min seats 150
            RouteRequirement req2 = new RouteRequirement(600.0, 150);
            FlightRoute route2 = new FlightRoute("route-lis-mad", lis, mad, 502.0, 80, req2, "Bootstrapper");
            flightRouteRepository.save(route2);

            // route-mad-opo: ACTIVE, 420 km, min range 500, min seats 120
            RouteRequirement req3 = new RouteRequirement(500.0, 120);
            FlightRoute route3 = new FlightRoute("route-mad-opo", mad, opo, 420.0, 70, req3, "Bootstrapper");
            flightRouteRepository.save(route3);

            // route-opo-mad-old: DEACTIVATED, 420 km, min range 500, min seats 120
            RouteRequirement req4 = new RouteRequirement(500.0, 120);
            FlightRoute route4 = new FlightRoute("route-opo-mad-old", opo, mad, 420.0, 70, req4, "Bootstrapper");
            route4.deactivate("Bootstrapper");
            flightRouteRepository.save(route4);

            // Partitioned routes
            if (airportRepository.findByIataCode_Code("BCN").isPresent()) {
                Airport bcn = airportRepository.findByIataCode_Code("BCN").get();
                RouteRequirement reqBcn = new RouteRequirement(1000.0, 150);
                FlightRoute routeBcn = new FlightRoute("route-opo-bcn", opo, bcn, 900.0, 120, reqBcn, "Bootstrapper");
                flightRouteRepository.save(routeBcn);
            }

            if (airportRepository.findByIataCode_Code("FAO").isPresent()) {
                Airport fao = airportRepository.findByIataCode_Code("FAO").get();
                RouteRequirement reqFao = new RouteRequirement(300.0, 100);
                FlightRoute routeFao = new FlightRoute("route-fao-lis", fao, lis, 220.0, 35, reqFao, "Bootstrapper");
                flightRouteRepository.save(routeFao);
            }

            System.out.println(" -> Bootstrapped routes for [" + clusterConfig.getSelfName() + "]");
        }
    }
}
