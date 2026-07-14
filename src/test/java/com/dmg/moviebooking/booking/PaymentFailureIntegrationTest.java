package com.dmg.moviebooking.booking;

import com.dmg.moviebooking.booking.entity.ShowSeatStatus;
import com.dmg.moviebooking.payment.service.PaymentGateway;
import com.dmg.moviebooking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SimulatedPaymentGateway always succeeds by design (see its Javadoc) — this is the one place the
 * payment-declined branch of BookingService.confirm() is actually exercised, via a substitute
 * gateway bean rather than making the simulated one flaky.
 */
@Import(PaymentFailureIntegrationTest.FailingGatewayConfig.class)
class PaymentFailureIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class FailingGatewayConfig {
        @Bean
        @Primary
        PaymentGateway failingPaymentGateway() {
            return amount -> new PaymentGateway.PaymentResult(false, null);
        }
    }

    @Test
    void declinedPaymentRollsBackAndLeavesTheHoldIntact() throws Exception {
        String adminToken = adminLogin();
        Long showId = createShowFixture(adminToken);
        Long seatId = seatIdsFor(showId).get(0);
        String customerToken = registerAndLogin("declined-" + System.nanoTime() + "@test.com", "password123");

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
                .andExpect(status().isPaymentRequired());

        // The whole transaction rolled back — booking is still PENDING_PAYMENT and the seat is
        // still HELD (under this same booking), not lost or double-released.
        mockMvc.perform(get("/api/v1/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        mockMvc.perform(get("/api/v1/shows/" + showId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value(ShowSeatStatus.HELD.name()));
    }
}
