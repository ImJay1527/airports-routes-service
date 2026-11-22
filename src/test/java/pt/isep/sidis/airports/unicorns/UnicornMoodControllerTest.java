package pt.isep.sidis.airports.unicorns;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UnicornMoodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetAllAirplaneMoods() throws Exception {
        mockMvc.perform(get("/api/v2/unicorns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[0].airplaneModel", notNullValue()))
                .andExpect(jsonPath("$[0].mood", notNullValue()))
                .andExpect(jsonPath("$[0].vibeScore", notNullValue()))
                .andExpect(jsonPath("$[0].cqrsRole", containsString("CQRS Read Projection")));
    }

    @Test
    void testGetAirplaneMoodByModel() throws Exception {
        mockMvc.perform(get("/api/v2/unicorns").param("model", "A320neo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].airplaneModel").value("A320neo"))
                .andExpect(jsonPath("$[0].mood").value("ECSTATIC"))
                .andExpect(jsonPath("$[0].sparklesEnabled").value(true));
    }

    @Test
    void testGetAirplaneMoodByPath() throws Exception {
        mockMvc.perform(get("/api/v2/unicorns/A320neo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.airplaneModel").value("A320neo"))
                .andExpect(jsonPath("$.mood").value("ECSTATIC"))
                .andExpect(jsonPath("$.sparklesEnabled").value(true));
    }

    @Test
    void testGetAirplaneMoodNonExistentModel() throws Exception {
        mockMvc.perform(get("/api/v2/unicorns").param("model", "NON_EXISTENT_PLANE"))
                .andExpect(status().isNotFound());
    }
}
