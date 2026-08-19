package de.atruvia.stablecoin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.config.ratelimit.RateLimitingFilter;
import de.atruvia.stablecoin.service.b2b.KillSwitchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.jwt-secret}")
    private String jwtSecret;

    @Value("${app.security.dev-mode:false}")
    private boolean devMode;

    @Value("${app.security.mtls-enabled:false}")
    private boolean mtlsEnabled;

    @Value("${app.rate-limit.small-vb-per-minute:20}")
    private int smallVbPerMinute;

    @Value("${app.rate-limit.large-vb-per-minute:100}")
    private int largeVbPerMinute;

    @Value("${app.rate-limit.marktbank-per-minute:500}")
    private int marktbankPerMinute;

    @Value("${app.rate-limit.anon-per-second:5}")
    private int anonPerSecond;

    @Autowired
    private KillSwitchService killSwitchService;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/auth/dev-token",
                                "/api/v1/b2b/inbound/webhook",
                                // Dev: Kafka-Event-Inspektion und S3-Export-Download
                                "/api/v1/dev/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(killSwitchFilter(), JwtAuthFilter.class)
                .addFilterAfter(rateLimitingFilter(), JwtAuthFilter.class);
        if (mtlsEnabled) {
            http.requiresChannel(c -> c.anyRequest().requiresSecure());
        }
        return http.build();
    }

    @Bean
    public KillSwitchFilter killSwitchFilter() {
        return new KillSwitchFilter(killSwitchService, objectMapper);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtSecret, devMode);
    }

    @Bean
    public RateLimitingFilter rateLimitingFilter() {
        return new RateLimitingFilter(smallVbPerMinute, largeVbPerMinute,
                marktbankPerMinute, anonPerSecond, objectMapper);
    }
}
