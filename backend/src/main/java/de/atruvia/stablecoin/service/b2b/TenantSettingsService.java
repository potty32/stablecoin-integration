package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.entity.TenantSettings;
import de.atruvia.stablecoin.repository.TenantSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Lädt mandantenspezifische Konfiguration aus tenant_settings.
 * Fällt auf Default-TenantSettings zurück, wenn kein Eintrag vorhanden.
 * Kein @Cacheable — tenant_settings werden selten geändert, aber Konsistenz ist kritisch.
 */
@Service
public class TenantSettingsService {

    private static final Logger log = LoggerFactory.getLogger(TenantSettingsService.class);

    private final TenantSettingsRepository repository;

    public TenantSettingsService(TenantSettingsRepository repository) {
        this.repository = repository;
    }

    /**
     * Lädt Settings für einen Tenant. Fallback auf Defaults wenn kein DB-Eintrag vorhanden.
     * Keine Exception — das System läuft sicher mit Defaults weiter.
     */
    public TenantSettings get(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return defaultSettings("unknown");
        }
        return repository.findById(tenantId)
                .orElseGet(() -> {
                    log.warn("[TENANT-SETTINGS] Kein Eintrag für tenant='{}' — Default-Werte verwendet", tenantId);
                    return defaultSettings(tenantId);
                });
    }

    @Transactional
    public TenantSettings activateKillSwitch(String tenantId, String reason, String activatedBy) {
        TenantSettings s = get(tenantId);
        s.setKillSwitchActive(true);
        s.setKillSwitchReason(reason);
        s.setKillSwitchBy(activatedBy);
        s.setKillSwitchAt(LocalDateTime.now());
        return repository.save(s);
    }

    @Transactional
    public TenantSettings deactivateKillSwitch(String tenantId) {
        TenantSettings s = get(tenantId);
        s.setKillSwitchActive(false);
        s.setKillSwitchReason(null);
        s.setKillSwitchBy(null);
        s.setKillSwitchAt(null);
        return repository.save(s);
    }

    @Transactional
    public TenantSettings update(TenantSettings settings) {
        settings.setTenantId(settings.getTenantId());
        return repository.save(settings);
    }

    private TenantSettings defaultSettings(String tenantId) {
        TenantSettings defaults = new TenantSettings();
        defaults.setTenantId(tenantId);
        return defaults;  // alle Felder haben Java-seitige Defaults
    }
}
