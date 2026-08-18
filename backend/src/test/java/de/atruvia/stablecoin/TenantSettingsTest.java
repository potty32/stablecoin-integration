package de.atruvia.stablecoin;

import de.atruvia.stablecoin.entity.TenantSettings;
import de.atruvia.stablecoin.repository.TenantSettingsRepository;
import de.atruvia.stablecoin.service.b2b.TenantSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für TenantSettingsService (G-03).
 */
class TenantSettingsTest {

    @Mock TenantSettingsRepository repository;
    private TenantSettingsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TenantSettingsService(repository);
    }

    @Test
    @DisplayName("TC1: get() mit bestehendem Tenant → korrekte Settings aus DB")
    void get_existingTenant_returnsDbSettings() {
        TenantSettings dbSettings = new TenantSettings();
        dbSettings.setTenantId("tenant-kleine-vb");
        dbSettings.setFxSpreadB2b(new BigDecimal("0.0020"));
        dbSettings.setFeeFlatB2bEur(new BigDecimal("3.00"));
        dbSettings.setApprovalThresholdB2b(new BigDecimal("30000.00"));

        when(repository.findById("tenant-kleine-vb")).thenReturn(Optional.of(dbSettings));

        TenantSettings result = service.get("tenant-kleine-vb");

        assertThat(result.getFxSpreadB2b()).isEqualByComparingTo(new BigDecimal("0.0020"));
        assertThat(result.getFeeFlatB2bEur()).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(result.getApprovalThresholdB2b()).isEqualByComparingTo(new BigDecimal("30000.00"));
    }

    @Test
    @DisplayName("TC2: get() ohne DB-Eintrag → Default-Werte (kein Fehler)")
    void get_missingTenant_returnsDefaults() {
        when(repository.findById("tenant-neu")).thenReturn(Optional.empty());

        TenantSettings result = service.get("tenant-neu");

        // Alle Defaults aus TenantSettings-Entity
        assertThat(result.getFxSpreadB2b()).isEqualByComparingTo(new BigDecimal("0.001500"));
        assertThat(result.getFeeFlatB2bEur()).isEqualByComparingTo(new BigDecimal("2.500000"));
        assertThat(result.getApprovalThresholdB2b()).isEqualByComparingTo(new BigDecimal("25000.000000"));
        assertThat(result.getSlippageToleranceBps()).isEqualTo(100);
        assertThat(result.isKillSwitchActive()).isFalse();
    }

    @Test
    @DisplayName("TC3: get() mit null tenantId → Default-Werte (kein NPE)")
    void get_nullTenantId_returnsDefaults() {
        TenantSettings result = service.get(null);
        assertThat(result).isNotNull();
        assertThat(result.isKillSwitchActive()).isFalse();
        verifyNoInteractions(repository);
    }
}
