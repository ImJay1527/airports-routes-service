package pt.isep.sidis.airports.cluster;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for the instance's cluster topology and peer replica endpoints.
 */
@Component
public class ClusterConfig {

    private final String selfName;
    private final List<String> peerUrls;

    public ClusterConfig(@Value("${airports.instance-name:airports-1}") String selfName,
                         @Value("${airports.peers:}") String peersEnv) {
        this.selfName = selfName;
        this.peerUrls = new ArrayList<>();
        if (peersEnv != null && !peersEnv.isBlank()) {
            Arrays.stream(peersEnv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(this.peerUrls::add);
        }
    }

    public String getSelfName() {
        return selfName;
    }

    public List<String> getPeerUrls() {
        return List.copyOf(peerUrls);
    }

    public boolean hasPeers() {
        return !peerUrls.isEmpty();
    }
}
