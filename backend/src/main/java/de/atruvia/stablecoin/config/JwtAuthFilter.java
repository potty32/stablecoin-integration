package de.atruvia.stablecoin.config;

import de.atruvia.stablecoin.config.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final String jwtSecret;
    private final boolean devMode;

    public JwtAuthFilter(String jwtSecret, boolean devMode) {
        this.jwtSecret = jwtSecret;
        this.devMode = devMode;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Wenn bereits eine Authentifizierung im SecurityContext gesetzt ist
        // (z.B. durch @WithMockUser in Tests), nicht überschreiben
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (devMode && (authHeader == null || authHeader.isBlank())) {
            // Dev-Modus: ohne Token mit Default-Mandant durchlassen
            setAuthentication("dev-user", null);
            TenantContext.set("tenant-default");
            try {
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                String tenantId = claims.get("tenant", String.class);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                MDC.put("userId", userId);
                MDC.put("tenantId", tenantId != null ? tenantId : "tenant-default");
                setAuthentication(userId, roles);
                TenantContext.set(tenantId != null ? tenantId : "tenant-default");
            } catch (Exception e) {
                log.warn("[JWT] Invalid token: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                return;
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    private void setAuthentication(String userId, List<String> roles) {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (roles != null) {
            roles.stream()
                 .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                 .forEach(authorities::add);
        }
        var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
