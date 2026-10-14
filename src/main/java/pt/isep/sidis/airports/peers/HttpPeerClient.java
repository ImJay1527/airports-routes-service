package pt.isep.sidis.airports.peers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import pt.isep.sidis.airports.cluster.ClusterConfig;
import pt.isep.sidis.airports.common.security.JwtUtils;
import pt.isep.sidis.airports.internal.AirportInfo;
import pt.isep.sidis.airports.internal.RouteInfo;

import java.time.Duration;
import java.util.Optional;

/**
 * Dispatches Unicast HTTP GET requests to peer replica instances when data is not held locally.
 * Includes loop-prevention header X-Forwarded-From and propagates X-Request-Id.
 */
@Component
public class HttpPeerClient implements PeerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPeerClient.class);

    private final ClusterConfig clusterConfig;
    private final JwtUtils jwtUtils;
    private final RestClient restClient;

    @Autowired
    public HttpPeerClient(ClusterConfig clusterConfig,
                          JwtUtils jwtUtils,
                          @Value("${airports.peer-timeout-ms:1500}") long timeoutMs) {
        this.clusterConfig = clusterConfig;
        this.jwtUtils = jwtUtils;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public HttpPeerClient(ClusterConfig clusterConfig, JwtUtils jwtUtils, RestClient restClient) {
        this.clusterConfig = clusterConfig;
        this.jwtUtils = jwtUtils;
        this.restClient = restClient;
    }

    /**
     * Queries peer replicas for an airport by IATA code.
     * Loops through configured peers using unicast HTTP GET.
     */
    @Override
    public Optional<AirportInfo> fetchAirportFromPeers(String iata, String requestId) {
        if (!clusterConfig.hasPeers()) {
            return Optional.empty();
        }

        String serviceToken = "Bearer " + jwtUtils.generateServiceToken(clusterConfig.getSelfName());

        for (String peerUrl : clusterConfig.getPeerUrls()) {
            try {
                log.debug("Forwarding airport query for {} to peer {}", iata, peerUrl);
                AirportInfo info = restClient.get()
                        .uri(peerUrl + "/internal/airports/{iata}", iata)
                        .header(HttpHeaders.AUTHORIZATION, serviceToken)
                        .header("X-Forwarded-From", clusterConfig.getSelfName())
                        .header("X-Request-Id", requestId != null ? requestId : "")
                        .retrieve()
                        .body(AirportInfo.class);

                if (info != null) {
                    log.info("Airport {} retrieved from peer clone {}", iata, peerUrl);
                    return Optional.of(info);
                }
            } catch (HttpClientErrorException.NotFound e) {
                log.debug("Peer {} does not hold airport {}", peerUrl, iata);
            } catch (Exception e) {
                log.warn("Failed to retrieve airport {} from peer {}: {}", iata, peerUrl, e.getMessage());
            }
        }

        return Optional.empty();
    }

    /**
     * Queries peer replicas for a flight route by routeId.
     * Loops through configured peers using unicast HTTP GET.
     */
    @Override
    public Optional<RouteInfo> fetchRouteFromPeers(String routeId, String requestId) {
        if (!clusterConfig.hasPeers()) {
            return Optional.empty();
        }

        String serviceToken = "Bearer " + jwtUtils.generateServiceToken(clusterConfig.getSelfName());

        for (String peerUrl : clusterConfig.getPeerUrls()) {
            try {
                log.debug("Forwarding route query for {} to peer {}", routeId, peerUrl);
                RouteInfo info = restClient.get()
                        .uri(peerUrl + "/internal/routes/{routeId}", routeId)
                        .header(HttpHeaders.AUTHORIZATION, serviceToken)
                        .header("X-Forwarded-From", clusterConfig.getSelfName())
                        .header("X-Request-Id", requestId != null ? requestId : "")
                        .retrieve()
                        .body(RouteInfo.class);

                if (info != null) {
                    log.info("Route {} retrieved from peer clone {}", routeId, peerUrl);
                    return Optional.of(info);
                }
            } catch (HttpClientErrorException.NotFound e) {
                log.debug("Peer {} does not hold route {}", peerUrl, routeId);
            } catch (Exception e) {
                log.warn("Failed to retrieve route {} from peer {}: {}", routeId, peerUrl, e.getMessage());
            }
        }

        return Optional.empty();
    }
}
