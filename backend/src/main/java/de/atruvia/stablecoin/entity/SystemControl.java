package de.atruvia.stablecoin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Globaler Emergency-Stop (DORA Art. 17, §25a KWG).
 * scope='GLOBAL' = systemweiter Kill Switch (überschreibt Tenant-Einstellung).
 * Kein TenantEntityListener — system_control ist tenant-unabhängig.
 */
@Entity
@Table(name = "system_control")
public class SystemControl {

    @Id
    @Column(name = "scope", length = 50)
    private String scope;

    @Column(name = "kill_switch_active", nullable = false)
    private boolean killSwitchActive = false;

    @Column(name = "kill_switch_reason")
    private String killSwitchReason;

    @Column(name = "kill_switch_by", length = 100)
    private String killSwitchBy;

    @Column(name = "kill_switch_at")
    private LocalDateTime killSwitchAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public boolean isKillSwitchActive() { return killSwitchActive; }
    public void setKillSwitchActive(boolean killSwitchActive) { this.killSwitchActive = killSwitchActive; }
    public String getKillSwitchReason() { return killSwitchReason; }
    public void setKillSwitchReason(String killSwitchReason) { this.killSwitchReason = killSwitchReason; }
    public String getKillSwitchBy() { return killSwitchBy; }
    public void setKillSwitchBy(String killSwitchBy) { this.killSwitchBy = killSwitchBy; }
    public LocalDateTime getKillSwitchAt() { return killSwitchAt; }
    public void setKillSwitchAt(LocalDateTime killSwitchAt) { this.killSwitchAt = killSwitchAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
