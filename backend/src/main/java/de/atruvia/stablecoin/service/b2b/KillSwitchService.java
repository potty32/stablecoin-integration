package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.entity.SystemControl;
import de.atruvia.stablecoin.entity.TenantSettings;
import de.atruvia.stablecoin.repository.SystemControlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * G-07: Aktiviert und deaktiviert den globalen Emergency-Stop und Mandanten-Kill-Switch.
 * Globaler Schalter (scope='GLOBAL') überschreibt alle Mandanten-Einstellungen.
 */
@Service
public class KillSwitchService {

    private static final Logger log = LoggerFactory.getLogger(KillSwitchService.class);
    private static final String GLOBAL_SCOPE = "GLOBAL";

    private final SystemControlRepository systemControlRepository;
    private final TenantSettingsService tenantSettingsService;

    public KillSwitchService(SystemControlRepository systemControlRepository,
                              TenantSettingsService tenantSettingsService) {
        this.systemControlRepository = systemControlRepository;
        this.tenantSettingsService = tenantSettingsService;
    }

    public boolean isGlobalKillSwitchActive() {
        return systemControlRepository.findById(GLOBAL_SCOPE)
                .map(SystemControl::isKillSwitchActive)
                .orElse(false);
    }

    public boolean isTenantKillSwitchActive(String tenantId) {
        if (tenantId == null) return false;
        TenantSettings s = tenantSettingsService.get(tenantId);
        return s.isKillSwitchActive();
    }

    public SystemControl getGlobalStatus() {
        return systemControlRepository.findById(GLOBAL_SCOPE)
                .orElseThrow(() -> new IllegalStateException("system_control GLOBAL-Eintrag fehlt"));
    }

    @Transactional
    public SystemControl activateGlobal(String reason, String activatedBy) {
        SystemControl sc = getGlobalStatus();
        sc.setKillSwitchActive(true);
        sc.setKillSwitchReason(reason);
        sc.setKillSwitchBy(activatedBy);
        sc.setKillSwitchAt(LocalDateTime.now());
        SystemControl saved = systemControlRepository.save(sc);
        log.error("[KILL-SWITCH] GLOBAL aktiviert durch={} reason={}", activatedBy, reason);
        return saved;
    }

    @Transactional
    public SystemControl deactivateGlobal(String deactivatedBy) {
        SystemControl sc = getGlobalStatus();
        sc.setKillSwitchActive(false);
        sc.setKillSwitchReason(null);
        sc.setKillSwitchBy(null);
        sc.setKillSwitchAt(null);
        SystemControl saved = systemControlRepository.save(sc);
        log.warn("[KILL-SWITCH] GLOBAL deaktiviert durch={}", deactivatedBy);
        return saved;
    }

    @Transactional
    public TenantSettings activateTenant(String tenantId, String reason, String activatedBy) {
        TenantSettings s = tenantSettingsService.activateKillSwitch(tenantId, reason, activatedBy);
        log.error("[KILL-SWITCH] TENANT={} aktiviert durch={} reason={}", tenantId, activatedBy, reason);
        return s;
    }

    @Transactional
    public TenantSettings deactivateTenant(String tenantId, String deactivatedBy) {
        TenantSettings s = tenantSettingsService.deactivateKillSwitch(tenantId);
        log.warn("[KILL-SWITCH] TENANT={} deaktiviert durch={}", tenantId, deactivatedBy);
        return s;
    }
}
