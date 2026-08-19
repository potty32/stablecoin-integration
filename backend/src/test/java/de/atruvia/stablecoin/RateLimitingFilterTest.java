package de.atruvia.stablecoin;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.config.ratelimit.RateLimitingFilter;
import de.atruvia.stablecoin.config.ratelimit.TenantType;
import de.atruvia.stablecoin.config.ratelimit.TokenBucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-Tests für RateLimitingFilter — kein Spring-Kontext.
 * Verifiziert Token-Bucket-Algorithmus, Tenant-Typ-Erkennung und HTTP-429-Verhalten.
 */
class RateLimitingFilterTest {

    private ObjectMapper objectMapper;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Sehr enge Limits zum Testen der 429-Logik
        filter = new RateLimitingFilter(2, 5, 10, 2, objectMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── TC-RL-01: IP-basiertes Rate Limiting ─────────────────────────────────

    @Test
    @DisplayName("TC-RL-01: IP-Limit (2/sec) — nach 2 Anfragen → 429 RATE_LIMIT_EXCEEDED")
    void ipRateLimit_exceedsLimit_returns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/b2b/inbound/webhook");
        request.setRemoteAddr("10.0.0.1");

        MockHttpServletResponse response1 = new MockHttpServletResponse();
        filter.doFilter(request, response1, new MockFilterChain());
        assertThat(response1.getStatus()).isNotEqualTo(429);

        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request, response2, new MockFilterChain());
        assertThat(response2.getStatus()).isNotEqualTo(429);

        // 3. Anfrage → Limit überschritten
        MockHttpServletResponse response3 = new MockHttpServletResponse();
        filter.doFilter(request, response3, new MockFilterChain());
        assertThat(response3.getStatus()).isEqualTo(429);
        assertThat(response3.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("TC-RL-02: IP-Limit — X-Forwarded-For-Header wird ausgewertet")
    void ipRateLimit_xForwardedFor_usedAsClientIp() throws Exception {
        // Gleiche forwarded IP → gleicher Bucket
        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/v1/b2b/inbound/webhook");
        request1.setRemoteAddr("192.168.1.1");
        request1.addHeader("X-Forwarded-For", "203.0.113.42");

        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/b2b/inbound/webhook");
        request2.setRemoteAddr("192.168.1.2");   // Anderer Proxy
        request2.addHeader("X-Forwarded-For", "203.0.113.42");  // Gleicher Client

        filter.doFilter(request1, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request2, new MockHttpServletResponse(), new MockFilterChain());

        // Dritte Anfrage desselben Clients → 429
        MockHttpServletResponse response3 = new MockHttpServletResponse();
        MockHttpServletRequest request3 = new MockHttpServletRequest("GET", "/api/v1/b2b/inbound/webhook");
        request3.setRemoteAddr("192.168.1.3");
        request3.addHeader("X-Forwarded-For", "203.0.113.42");
        filter.doFilter(request3, response3, new MockFilterChain());
        assertThat(response3.getStatus()).isEqualTo(429);
    }

    // ─── TC-RL-03: Tenant-basiertes Rate Limiting ─────────────────────────────

    @Test
    @DisplayName("TC-RL-03: Tenant SMALL_VB (Limit=2) — 3 Anfragen → 429 nach der 3.")
    void tenantRateLimit_smallVb_exceedsLimit_returns429() throws Exception {
        TenantContext.set("tenant-vb-001");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/b2b/transfers");
        request.setRemoteAddr("10.0.0.5");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse response3 = new MockHttpServletResponse();
        filter.doFilter(request, response3, new MockFilterChain());
        assertThat(response3.getStatus()).isEqualTo(429);
        assertThat(response3.getContentType()).contains("application/json");
    }

    @Test
    @DisplayName("TC-RL-04: Tenant MARKTBANK (Limit=10) — 10 Anfragen erlaubt")
    void tenantRateLimit_marktbank_higherLimit() throws Exception {
        TenantContext.set("tenant-marktbank-001");

        // Marktbank hat Limit 10 (zweiter Parameter "10" in Filter-Konstruktor)
        // Mit limit=10 sollten 10 Anfragen kein 429 triggern
        RateLimitingFilter filterMarkt = new RateLimitingFilter(2, 5, 10, 2, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/b2b/transfers");
        request.setRemoteAddr("10.0.0.6");

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filterMarkt.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).as("Anfrage %d sollte nicht geblockt werden", i + 1)
                    .isNotEqualTo(429);
        }

        // 11. Anfrage → 429
        MockHttpServletResponse response11 = new MockHttpServletResponse();
        filterMarkt.doFilter(request, response11, new MockFilterChain());
        assertThat(response11.getStatus()).isEqualTo(429);
    }

    // ─── TC-RL-04: Exempt-Pfade ───────────────────────────────────────────────

    @Test
    @DisplayName("TC-RL-05: /actuator/health ist exempt — kein Rate Limiting")
    void exemptPath_actuatorHealth_neverBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("10.0.0.99");

        // Viele Anfragen — keines soll 429 sein
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    @DisplayName("TC-RL-06: /api-docs ist exempt — kein Rate Limiting")
    void exemptPath_apiDocs_neverBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api-docs");
        request.setRemoteAddr("10.0.0.98");

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    // ─── TC-RL-07: HTTP-429-Antwortformat ────────────────────────────────────

    @Test
    @DisplayName("TC-RL-07: 429-Antwort enthält RATE_LIMIT_EXCEEDED, Retry-After-Header")
    void rateLimitResponse_hasCorrectFormat() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/b2b/inbound/webhook");
        request.setRemoteAddr("10.10.10.1");

        // Limit ausschöpfen
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse response429 = new MockHttpServletResponse();
        filter.doFilter(request, response429, new MockFilterChain());

        assertThat(response429.getStatus()).isEqualTo(429);
        assertThat(response429.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response429.getContentType()).contains("application/json");

        String body = response429.getContentAsString();
        assertThat(body).contains("RATE_LIMIT_EXCEEDED");
        assertThat(body).contains("message");
        assertThat(body).contains("timestamp");
    }

    // ─── TC-RL-08: Tenant-Typ-Erkennung ──────────────────────────────────────

    @Test
    @DisplayName("TC-RL-08: Tenant-IDs mit 'marktbank' → MARKTBANK-Limit")
    void tenantTypeResolution_marktbankInId() throws Exception {
        TenantContext.set("tenant-marktbank-2026");

        // Marktbank hat limit=10 in unserem Test-Filter
        // SMALL_VB hätte limit=2, LARGE_VB=5
        // 6 Anfragen sollten alle durchkommen (MARKTBANK=10)
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/b2b/transfers");
        request.setRemoteAddr("10.0.0.50");

        for (int i = 0; i < 6; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).as("Anfrage %d", i + 1).isNotEqualTo(429);
        }
    }

    @Test
    @DisplayName("TC-RL-09: Tenant-IDs mit 'large' → LARGE_VB-Limit")
    void tenantTypeResolution_largeInId() throws Exception {
        TenantContext.set("tenant-vb-large-001");

        // LARGE_VB hat limit=5, SMALL_VB=2
        // 5 Anfragen kommen durch, 6. wird geblockt
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/b2b/transfers");
        request.setRemoteAddr("10.0.0.51");

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).as("Anfrage %d", i + 1).isNotEqualTo(429);
        }

        MockHttpServletResponse response6 = new MockHttpServletResponse();
        filter.doFilter(request, response6, new MockFilterChain());
        assertThat(response6.getStatus()).isEqualTo(429);
    }

    // ─── TC-RL-10: TokenBucket Unit-Test ─────────────────────────────────────

    @Test
    @DisplayName("TC-RL-10: TokenBucket mit 3 Tokens — genau 3 Anfragen erlaubt")
    void tokenBucket_exactCapacity() {
        TokenBucket bucket = TokenBucket.ofRequestsPerSecond(3);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();  // 4. → leer
    }

    @Test
    @DisplayName("TC-RL-11: TokenBucket — thread-sicher unter parallelem Zugriff")
    void tokenBucket_threadSafe() throws InterruptedException {
        int capacity = 100;
        TokenBucket bucket = TokenBucket.ofRequestsPerMinute(capacity);

        java.util.concurrent.atomic.AtomicInteger allowed = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger rejected = new java.util.concurrent.atomic.AtomicInteger();

        Thread[] threads = new Thread[20];
        for (int i = 0; i < 20; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    if (bucket.tryConsume()) allowed.incrementAndGet();
                    else rejected.incrementAndGet();
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // Exakt capacity Tokens vergeben worden
        assertThat(allowed.get()).isEqualTo(capacity);
        assertThat(rejected.get()).isEqualTo(200 - capacity);
    }
}
