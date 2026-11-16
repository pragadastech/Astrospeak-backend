package com.astrotalk.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferralMathTest {
    @Test
    void onePercentOfThePlanPrice() {
        assertEquals(15, StudioService.planShare(1500));
        assertEquals(5, StudioService.planShare(500));
        assertEquals(25, StudioService.planShare(2499));
        assertEquals(50, StudioService.planShare(4999));
        assertEquals(0, StudioService.planShare(0));
    }
}
