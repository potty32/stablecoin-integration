package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.dto.TaxReportRequestDto;
import de.atruvia.stablecoin.client.dto.TaxReportResponseDto;
import de.atruvia.stablecoin.client.mock.MockAtruviaTaxClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-Tests für MockAtruviaTaxClient (G-02 — Drittsystem-Mock).
 */
class MockAtruviaTaxClientTest {

    private MockAtruviaTaxClient client;

    @BeforeEach
    void setUp() {
        client = new MockAtruviaTaxClient();
    }

    @Test
    @DisplayName("TC-TAX-01: Ertrag ≤ 1000 EUR → FSA_COVERED, kein Steuerabzug")
    void smallYield_fsaCovered_noTax() {
        TaxReportRequestDto request = new TaxReportRequestDto(
                "cust-b2c-001", "tenant-default",
                new BigDecimal("50.00"), 2026, "redeem-test-1");

        TaxReportResponseDto response = client.reportCapitalGain(request);

        assertThat(response.status()).isEqualTo("FSA_COVERED");
        assertThat(response.taxWithheldEur()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.netPayoutEur()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.taxReferenceId()).startsWith("TAX-MOCK-");
    }

    @Test
    @DisplayName("TC-TAX-02: Ertrag > 1000 EUR → TAX_APPLIED, KapErSt + SoliZ korrekt")
    void largeYield_taxApplied_correctCalculation() {
        // Ertrag 1500 EUR: FSA=1000, taxable=500
        // KapErSt: 500 × 25% = 125,00
        // SoliZ:  125 × 5,5% =   6,88
        // Total: 131,88 EUR Steuer
        TaxReportRequestDto request = new TaxReportRequestDto(
                "cust-b2c-002", "tenant-default",
                new BigDecimal("1500.00"), 2026, "redeem-test-2");

        TaxReportResponseDto response = client.reportCapitalGain(request);

        // Mock nutzt immer 1000 EUR FSA → 1500 EUR Ertrag → 500 EUR taxable → PARTIAL_FSA
        assertThat(response.status()).isEqualTo("PARTIAL_FSA");
        assertThat(response.taxWithheldEur()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.taxWithheldEur()).isEqualByComparingTo(new BigDecimal("131.88"));
        assertThat(response.netPayoutEur()).isEqualByComparingTo(new BigDecimal("1368.12"));
    }

    @Test
    @DisplayName("TC-TAX-03: Ertrag genau 1000 EUR → FSA_COVERED, keine Steuer")
    void exactFsaLimit_fsaCovered() {
        TaxReportRequestDto request = new TaxReportRequestDto(
                "cust-b2c-003", "tenant-default",
                new BigDecimal("1000.00"), 2026, "redeem-test-3");

        TaxReportResponseDto response = client.reportCapitalGain(request);

        assertThat(response.status()).isEqualTo("FSA_COVERED");
        assertThat(response.taxWithheldEur()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
