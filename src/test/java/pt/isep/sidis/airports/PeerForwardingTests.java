package pt.isep.sidis.airports;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.airports.internal.AirportInfo;
import pt.isep.sidis.airports.internal.RouteInfo;
import pt.isep.sidis.airports.peers.PeerClient;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PeerForwardingTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PeerClient peerClient;

    @Test
    @WithMockUser(roles = "SERVICE")
    void testLocalHitDoesNotForwardToPeer() throws Exception {
        // LIS is bootstrapped locally
        mockMvc.perform(get("/internal/airports/LIS"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "local"))
                .andExpect(jsonPath("$.iataCode").value("LIS"));

        // Verify peer was NOT contacted
        verify(peerClient, never()).fetchAirportFromPeers(any(), any());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void testLocalMissForwardsToPeerAndReturnsAggregatedResult() throws Exception {
        // FAO is not in local database, but peer has it
        AirportInfo faoFromPeer = new AirportInfo("FAO", "OPERATIONAL", List.of("A320neo"));
        when(peerClient.fetchAirportFromPeers(eq("FAO"), any())).thenReturn(Optional.of(faoFromPeer));

        mockMvc.perform(get("/internal/airports/FAO"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "peer"))
                .andExpect(jsonPath("$.iataCode").value("FAO"))
                .andExpect(jsonPath("$.status").value("OPERATIONAL"));

        // Verify peer was contacted
        verify(peerClient, times(1)).fetchAirportFromPeers(eq("FAO"), any());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void testLoopPreventionWhenAlreadyForwarded() throws Exception {
        // Request contains X-Forwarded-From header -> Must NOT forward again!
        mockMvc.perform(get("/internal/airports/UNKNOWN")
                        .header("X-Forwarded-From", "airports-2"))
                .andExpect(status().isNotFound());

        // Verify peer was NEVER contacted because of loop prevention
        verify(peerClient, never()).fetchAirportFromPeers(any(), any());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void testLocalMissAndPeerMissReturnsNotFound() throws Exception {
        when(peerClient.fetchAirportFromPeers(eq("NONEXISTENT"), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/airports/NONEXISTENT"))
                .andExpect(status().isNotFound());

        verify(peerClient, times(1)).fetchAirportFromPeers(eq("NONEXISTENT"), any());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void testRouteForwardingWhenMissingLocally() throws Exception {
        AirportInfo opo = new AirportInfo("OPO", "OPERATIONAL", List.of("A320neo"));
        AirportInfo fao = new AirportInfo("FAO", "OPERATIONAL", List.of("A320neo"));
        RouteInfo routeFromPeer = new RouteInfo("route-opo-fao", "ACTIVE", 470.0, 500.0, 150, opo, fao);

        when(peerClient.fetchRouteFromPeers(eq("route-opo-fao"), any())).thenReturn(Optional.of(routeFromPeer));

        mockMvc.perform(get("/internal/routes/route-opo-fao"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "peer"))
                .andExpect(jsonPath("$.routeId").value("route-opo-fao"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.distanceKm").value(470.0));

        verify(peerClient, times(1)).fetchRouteFromPeers(eq("route-opo-fao"), any());
    }
}
