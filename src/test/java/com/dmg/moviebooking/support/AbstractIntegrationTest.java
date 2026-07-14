package com.dmg.moviebooking.support;

import com.dmg.moviebooking.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately NOT annotated @Transactional — the concurrency tests need each thread's call to
 * open its own real transaction/connection against Testcontainers Postgres. A transactional test
 * class would wrap everything in one connection and prove nothing.
 *
 * @AutoConfigureMockMvc (not a hand-built MockMvcBuilders.webAppContextSetup) is required for the
 * Spring Security filter chain — including JwtAuthenticationFilter — to actually run during
 * MockMvc dispatch; without it every request looks unauthenticated regardless of the Bearer token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    protected final ObjectMapper objectMapper = JsonMapper.builder().build();

    protected String registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Test User", "email", email, "password", password))))
                .andReturn();
        return login(email, password);
    }

    protected String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asString();
    }

    protected String adminLogin() throws Exception {
        return login("admin@moviebooking.local", "Admin@12345");
    }

    /** Creates a fresh city/theater/screen/8-seat layout/movie/show and returns the show id. */
    protected Long createShowFixture(String adminToken) throws Exception {
        Long cityId = postAsAdmin(adminToken, "/api/v1/admin/cities", Map.of("name", "TestCity-" + System.nanoTime()))
                .get("id").asLong();
        Long theaterId = postAsAdmin(adminToken, "/api/v1/admin/theaters",
                Map.of("cityId", cityId, "name", "TestTheater", "address", "Test Address"))
                .get("id").asLong();
        Long screenId = postAsAdmin(adminToken, "/api/v1/admin/screens",
                Map.of("theaterId", theaterId, "name", "Screen 1"))
                .get("id").asLong();
        postAsAdmin(adminToken, "/api/v1/admin/screens/" + screenId + "/seats", Map.of(
                "rows", List.of(
                        Map.of("rowLabel", "A", "seatCount", 4, "seatType", "PREMIUM"),
                        Map.of("rowLabel", "B", "seatCount", 4, "seatType", "REGULAR"))));
        Long movieId = postAsAdmin(adminToken, "/api/v1/admin/movies",
                Map.of("title", "Test Movie", "durationMinutes", 120, "language", "English", "genre", "Drama"))
                .get("id").asLong();
        Instant start = Instant.now().plus(5, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        return postAsAdmin(adminToken, "/api/v1/admin/shows",
                Map.of("movieId", movieId, "screenId", screenId, "startTime", start.toString(), "endTime", end.toString()))
                .get("id").asLong();
    }

    protected JsonNode postAsAdmin(String adminToken, String url, Object body) throws Exception {
        String response = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    /** Returns the show's ShowSeat ids in seat order (matches the A1..A4,B1..B4 layout above). */
    protected List<Long> seatIdsFor(Long showId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/shows/" + showId + "/seats"))
                .andReturn().getResponse().getContentAsString();
        JsonNode seats = objectMapper.readTree(response);
        List<Long> ids = new ArrayList<>();
        for (JsonNode seat : seats) {
            ids.add(seat.get("showSeatId").asLong());
        }
        return ids;
    }
}
