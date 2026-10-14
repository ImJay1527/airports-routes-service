package pt.isep.sidis.airports;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AirportsRoutesApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void testGetInternalAirport() throws Exception {
        mockMvc.perform(get("/internal/airports/LIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iataCode").value("LIS"))
                .andExpect(jsonPath("$.status").value("OPERATIONAL"))
                .andExpect(jsonPath("$.certifiedModels[0]").value("A320neo"));
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void testGetInternalRoute() throws Exception {
        mockMvc.perform(get("/internal/routes/route-opo-lis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value("route-opo-lis"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.distanceKm").value(277.0))
                .andExpect(jsonPath("$.minRangeRequired").value(350.0))
                .andExpect(jsonPath("$.minCapacityRequired").value(100))
                .andExpect(jsonPath("$.origin.iataCode").value("OPO"))
                .andExpect(jsonPath("$.destination.iataCode").value("LIS"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testInternalForbiddenForNonServiceUser() throws Exception {
        mockMvc.perform(get("/internal/airports/LIS"))
                .andExpect(status().isForbidden());
    }
}
