package de.atruvia.stablecoin.config.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.config.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DoS-Schutz per Token-Bucket-Algorithmus.
 *
 * Tenant-basiert (nach JWT-Authentifizierung via TenantContext):
 *   - SMALL_VB  → 20  req/min
 *   - LARGE_VB  → 100 req/min
 *   - MARKTBANK → 500 req/min
 *
 * IP-basiert für nicht authentifizierte Pfade (Webhooks, auth):
 *   - max. 5 req/sec je IP
 *
 * HTTP 429 RATE_LIMIT_EXCEEDED bei Überschreitung.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Pfade, die IP-basiertes Limiting verwenden (unauthentifiziert oder Webhook)
    private static final Set<String> IP_RATE_LIMITED_PREFIXES = Set.of(
            "/api/v1/b2b/inbound/webhook",
            "/api/v1/auth/",
            "/api/v1/dev/"
    );

    // Pfade komplett vom Rate-Limiting ausgenommen (z.B. Health-Checks)
    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/api-docs",
            "/swagger-ui"
    );

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private final int smallVbPerMinute;
    private final int largeVbPerMinute;
    private final int marktbankPerMinute;
    private final int anonPerSecond;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(int smallVbPerMinute, int largeVbPerMinute,
                               int marktbankPerMinute, int anonPerSecond,
                               ObjectMapper objectMapper) {
        this.smallVbPerMinute    = smallVbPerMinute;
        this.largeVbPerMinute    = largeVbPerMinute;
        this.marktbankPerMinute  = marktbankPerMinute;
        this.anonPerSecond       = anonPerSecond;
        this.objectMapper        = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isExempt(path)) {
            chain.doFilter(request, response);
            return;
        }

        String tenantId = TenantContext.get();

        if (tenantId == null || isIpRateLimitedPath(path)) {
            // Nicht authentifizierter Pfad → IP-basiertes Limiting
            String ip = resolveClientIp(request);
            if (!consumeIpBucket(ip)) {
                log.warn("[RATE-LIMIT] IP={} path={} exceeded anon limit ({}/sec)", ip, path, anonPerSecond);
                rejectWith429(response, "ip:" + ip);
                return;
            }
        } else {
            // Authentifizierter Mandant → Tenant-basiertes Limiting
            TenantType type = resolveTenantType(tenantId);
            if (!consumeTenantBucket(tenantId, type)) {
                log.warn("[RATE-LIMIT] tenant={} type={} path={} exceeded limit", tenantId, type, path);
                rejectWith429(response, "tenant:" + tenantId);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isExempt(String path) {
        return EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isIpRateLimitedPath(String path) {
        return IP_RATE_LIMITED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private TenantType resolveTenantType(String tenantId) {
        String lower = tenantId.toLowerCase();
        if (lower.contains("marktbank")) return TenantType.MARKTBANK;
        if (lower.contains("large") || lower.contains("gross")) return TenantType.LARGE_VB;
        return TenantType.SMALL_VB;
    }

    private boolean consumeTenantBucket(String tenantId, TenantType type) {
        return buckets.computeIfAbsent("tenant:" + tenantId, k -> createTenantBucket(type))
                      .tryConsume();
    }

    private boolean consumeIpBucket(String ip) {
        return buckets.computeIfAbsent("ip:" + ip, k -> TokenBucket.ofRequestsPerSecond(anonPerSecond))
                      .tryConsume();
    }

    private TokenBucket createTenantBucket(TenantType type) {
        return switch (type) {
            case SMALL_VB  -> TokenBucket.ofRequestsPerMinute(smallVbPerMinute);
            case LARGE_VB  -> TokenBucket.ofRequestsPerMinute(largeVbPerMinute);
            case MARKTBANK -> TokenBucket.ofRequestsPerMinute(marktbankPerMinute);
        };
    }

    // S-04-Fix: XFF wird nur berücksichtigt, wenn der direkte TCP-Peer (remoteAddr)
    // ein konfigurierter Trusted Proxy ist. Ohne Konfiguration → remoteAddr direkt nutzen.
    // Das verhindert IP-Spoofing durch beliebige X-Forwarded-For-Header.
    @org.springframework.beans.factory.annotation.Value(
            "${app.rate-limit.trusted-proxy-cidr:#{null}}")
    private String trustedProxyCidr;

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxyCidr != null && isTrustedProxy(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                // Rechtestes IP aus XFF nehmen — nicht das erste (client-kontrolliert)
                String[] parts = xff.split(",");
                return parts[parts.length - 1].trim();
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        // Einfacher Prefix-Check (für /24-Subnetze ausreichend, vollständiges CIDR via Apache Commons Net)
        return trustedProxyCidr != null && remoteAddr.startsWith(
                trustedProxyCidr.contains("/") ? trustedProxyCidr.substring(0, trustedProxyCidr.lastIndexOf('.')) : trustedProxyCidr
        );
    }

    private void rejectWith429(HttpServletResponse response, String identifier) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "60");
        Map<String, Object> body = Map.of(
                "errorCode",  "RATE_LIMIT_EXCEEDED",
                "message",    "Rate limit exceeded. Please slow down your requests.",
                "identifier", identifier,
                "timestamp",  LocalDateTime.now().toString()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
