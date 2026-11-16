package com.astrotalk.chart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScientificChartEngineTest {
    @Test
    void dashaHasThreeLevelsAndARunningPeriod() {
        Chart chart = ScientificChartEngine.compute("1990-05-12", "06:30:00", 17.3850, 78.4867, 5.5);
        assertEquals(9, chart.planets.size());
        assertEquals(12, chart.cusps.size());
        assertEquals(9, chart.dasha.periods.size());
        Chart.Period firstAd = chart.dasha.periods.get(0).antardashas.get(0);
        assertEquals(9, firstAd.pratyantars.size());
        assertNotNull(chart.currentDasha.mahadasha);
        assertNotNull(chart.currentDasha.antardasha);
        assertNotNull(chart.currentDasha.pratyantar);
        assertTrue(chart.currentDasha.label.contains(chart.currentDasha.mahadasha));
        int placed = chart.rasi.stream().mapToInt(java.util.List::size).sum();
        assertEquals(9, placed);
        assertEquals("Natal Chart", chart.input.get("chartName"));
        assertNotNull(chart.rulingPlanets.get("ascSubSubLord"));
        assertNotNull(chart.rulingPlanets.get("moonSubSubLord"));
    }

    @Test
    void divisionNumberSetsTheAscendantSub() {
        Chart plain = ScientificChartEngine.compute("1990-05-12", "06:30:00", 17.3850, 78.4867, 5.5);
        Chart numbered = ScientificChartEngine.compute("1990-05-12", "06:30:00", 17.3850, 78.4867, 5.5, 1);
        assertEquals(1, numbered.input.get("division"));
        assertNotEquals(plain.ascendant.dms, numbered.ascendant.dms);
        assertEquals(0.0, numbered.cusps.get(0).longitude, 1e-6);
        Chart last = ScientificChartEngine.compute("1990-05-12", "06:30:00", 17.3850, 78.4867, 5.5, 249);
        assertTrue(last.cusps.get(0).longitude > 300);
    }
}
