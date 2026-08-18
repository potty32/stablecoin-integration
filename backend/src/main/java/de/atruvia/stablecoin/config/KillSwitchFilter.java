package de.atruvia.stablecoin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.service.b2b.KillSwitchService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * G-07: Emergency-Stop-Filter (DORA Art. 17, §25a KWG).
 *
 * Blockiert alle schreibenden Requests (POST/PUT/PATCH/DELETE) wenn:
 * - Globaler Kill Switch aktiv (system_control.GLOBAL)
 * - Mandanten-Kill-Switch aktiv (tenant_settings.kill_switch_active)
 *
 * Ausnahmen (werden nie blockiert):
 * - GET-Requests (Read-only)
 * - Actuator/Health-Endpoints
 * - Auth-Endpoints (dev-token)
 * - Kill-Switch-Admin-Endpoints selbst (sonst kein Deaktivieren möglich)
 */
public class KillSwitchFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(KillSwitchFilter.class);

    private static final Set<String> WRITE_METHODS = Set.of(
            HttpMethod.POST.name(), HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(), HttpMethod.DELETE.name());

    private static final Set<String> PERMITTED_PATH_PREFIXES = Set.of(
            "/actuator", "/api/v1/auth",
            "/api/v1/b2b/admin/kill-switch",  // Kill-Switch-Admin darf immer zugreifen
            "/api-docs", "/swagger-ui");

    private final KillSwitchService killSwitchService;
    private final ObjectMapper objectMapper;

    public KillSwitchFilter(KillSwitchService killSwitchService, ObjectMapper objectMapper) {
        this.killSwitchService = killSwitchService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path   = request.getRequestURI();

        // Nur schreibende Methoden prüfen
        if (!WRITE_METHODS.contains(method) || isPermittedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Globaler Kill Switch
        if (killSwitchService.isGlobalKillSwitchActive()) {
            String reason = killSwitchService.getGlobalStatus().getKillSwitchReason();
            log.warn("[KILL-SWITCH] GLOBAL blockiert {} {}", method, path);
            rejectWith503(response, "Globaler Emergency-Stop aktiv: " + reason);
            return;
        }

        // Mandanten-Kill-Switch
        String tenantId = TenantContext.get();
        if (tenantId != null && killSwitchService.isTenantKillSwitchActive(tenantId)) {
            log.warn("[KILL-SWITCH] TENANT={} blockiert {} {}", tenantId, method, path);
            rejectWith503(response, "Zahlungsverkehr für Mandant " + tenantId + " eingefroren");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPermittedPath(String path) {
        return PERMITTED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void rejectWith503(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = Map.of(
                "errorCode", "SYSTEM_003",
                "message", message,
                "timestamp", LocalDateTime.now().toString());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
