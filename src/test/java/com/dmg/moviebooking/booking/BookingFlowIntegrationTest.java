package com.dmg.moviebooking.booking;

import com.dmg.moviebooking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void holdConfirmCancelHappyPath() throws Exception {
        String adminToken = adminLogin();
        Long showId = createShowFixture(adminToken);
        List<Long> seatIds = seatIdsFor(showId);
        String customerToken = registerAndLogin("happy-" + System.nanoTime() + "@test.com", "password123");

        String holdBody = objectMapper.writeValueAsString(Map.of("showId", showId, "seatIds", List.of(seatIds.get(0), seatIds.get(1))));
        String holdResponse = mockMvc.perform(post("/api/v1/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(holdResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/shows/" + showId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("BOOKED"));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/shows/" + showId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void doubleConfirmIsRejected() throws Exception {
        String adminToken = adminLogin();
        Long showId = createShowFixture(adminToken);
        Long seatId = seatIdsFor(showId).get(0);
        String customerToken = registerAndLogin("doubleconfirm-" + System.nanoTime() + "@test.com", "password123");

        String holdResponse = mockMvc.perform(post("/api/v1/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("showId", showId, "seatIds", List.of(seatId)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(holdResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownDiscountCodeIsRejectedAndHoldSurvives() throws Exception {
        String adminToken = adminLogin();
        Long showId = createShowFixture(adminToken);
        Long seatId = seatIdsFor(showId).get(0);
        String customerToken = registerAndLogin("baddiscount-" + System.nanoTime() + "@test.com", "password123");

        String holdResponse = mockMvc.perform(post("/api/v1/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("showId", showId, "seatIds", List.of(seatId)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(holdResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("discountCode", "DOES-NOT-EXIST"))))
                .andExpect(status().isUnprocessableEntity());

        // The failed confirm rolled back entirely — the hold must still be usable.
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/confirm")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("showId", 1, "seatIds", List.of(1)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotHitCustomerOnlyBookingEndpoint() throws Exception {
        String adminToken = adminLogin();
        mockMvc.perform(post("/api/v1/bookings/hold")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("showId", 1, "seatIds", List.of(1)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotHitAdminOnlyEndpoint() throws Exception {
        String customerToken = registerAndLogin("customer-rbac-" + System.nanoTime() + "@test.com", "password123");
        mockMvc.perform(post("/api/v1/admin/cities")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Nowhere"))))
                .andExpect(status().isForbidden());
    }
}
