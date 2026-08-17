package de.atruvia.stablecoin.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stellt sicher, dass TenantContext beim ersten Repository-Aufruf gesetzt ist.
 * Das eigentliche DB-Setzen (app.current_tenant) übernimmt TenantAwareDataSource
 * beim Connection-Acquire — dieser Aspect ist ein Monitoring-/Guard-Layer.
 */
@Aspect
@Component
public class TenantAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantAspect.class);

    @Around("execution(* de.atruvia.stablecoin.repository.*.*(..))")
    public Object guardTenantPresence(ProceedingJoinPoint pjp) throws Throwable {
        if (TenantContext.get() == null) {
            log.warn("[TENANT] Kein Tenant-Kontext beim Repository-Aufruf: {}",
                    pjp.getSignature().toShortString());
        }
        return pjp.proceed();
    }
}
