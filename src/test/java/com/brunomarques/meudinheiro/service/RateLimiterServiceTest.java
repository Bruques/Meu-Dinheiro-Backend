package com.brunomarques.meudinheiro.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }

    @Test
    void tryConsumeAi_allowsThreeRequestsPerMinute() {
        String userId = "user-ai-limit";

        assertTrue(rateLimiterService.tryConsumeAi(userId));
        assertTrue(rateLimiterService.tryConsumeAi(userId));
        assertTrue(rateLimiterService.tryConsumeAi(userId));
        assertFalse(rateLimiterService.tryConsumeAi(userId));
    }

    @Test
    void tryConsumeAi_differentUsersHaveSeparateBuckets() {
        String userA = "user-a";
        String userB = "user-b";

        rateLimiterService.tryConsumeAi(userA);
        rateLimiterService.tryConsumeAi(userA);
        rateLimiterService.tryConsumeAi(userA);

        assertTrue(rateLimiterService.tryConsumeAi(userB));
    }

    @Test
    void tryConsumeManual_allowsTwentyRequestsPerMinute() {
        String userId = "user-manual-limit";

        for (int i = 0; i < 20; i++) {
            assertTrue(rateLimiterService.tryConsumeManual(userId), "Request " + (i + 1) + " should be allowed");
        }
        assertFalse(rateLimiterService.tryConsumeManual(userId));
    }
}
