package de.atruvia.stablecoin.config;

import de.atruvia.stablecoin.entity.AddressBook;
import de.atruvia.stablecoin.entity.AuditLog;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TaxEvent;
import de.atruvia.stablecoin.entity.YieldPosition;
import jakarta.persistence.PrePersist;

/**
 * JPA EntityListener: setzt tenant_id automatisch aus TenantContext beim Persistieren.
 * Kein Spring-Injection nötig — TenantContext ist ein statischer ThreadLocal.
 */
public class TenantEntityListener {

    @PrePersist
    public void setTenantOnPersist(Object entity) {
        String tenantId = TenantContext.get();
        if (tenantId == null) return;

        if (entity instanceof CustomerAccount ca) {
            if (ca.getTenantId() == null) ca.setTenantId(tenantId);
        } else if (entity instanceof StablecoinTransaction tx) {
            if (tx.getTenantId() == null) tx.setTenantId(tenantId);
        } else if (entity instanceof AddressBook ab) {
            if (ab.getTenantId() == null) ab.setTenantId(tenantId);
        } else if (entity instanceof YieldPosition yp) {
            if (yp.getTenantId() == null) yp.setTenantId(tenantId);
        } else if (entity instanceof AuditLog al) {
            if (al.getTenantId() == null) al.setTenantId(tenantId);
        } else if (entity instanceof TaxEvent te) {
            if (te.getTenantId() == null) te.setTenantId(tenantId);
        }
    }
}
