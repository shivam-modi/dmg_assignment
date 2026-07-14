package com.dmg.moviebooking.booking;

import com.dmg.moviebooking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the booking flow against a fixture loaded straight into the schema via SQL
 * (src/test/resources/sql/seed-booking-fixture.sql), not built up through the admin API like
 * the other integration tests do. This is closer to how a data-migration script or a reporting
 * query against this schema would be validated, and it's a second, independent way of proving
 * the Flyway-managed schema and the JPA entity mappings actually agree with each other — if a
 * column got renamed in one but not the other, this test breaks even though the API-driven tests
 * might not notice (they'd never produce a row shaped like the raw SQL does).
 */
@Sql("/sql/seed-booking-fixture.sql")
class SqlSeededBookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void booksASeatFromSqlSeededFixtureData() throws Exception {
        Long showId = jdbcTemplate.queryForObject(
                "SELECT sh.id FROM shows sh JOIN movies m ON m.id = sh.movie_id WHERE m.title = 'SQL Seed Movie'",
                Long.class);
        List<Long> seatIds = jdbcTemplate.queryForList(
                "SELECT id FROM show_seats WHERE show_id = ? ORDER BY id", Long.class, showId);
        assertThat(seatIds).hasSize(8);

        String customerToken = registerAndLogin("sql-seed-" + System.nanoTime() + "@test.com", "password123");

        String holdResponse = mockMvc.perform(post("/api/v1/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("showId", showId, "seatIds", List.of(seatIds.get(0))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.seats[0].price").value(250.0))
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(holdResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        String bookedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM show_seats WHERE id = ?", String.class, seatIds.get(0));
        assertThat(bookedStatus).isEqualTo("BOOKED");
    }
}
