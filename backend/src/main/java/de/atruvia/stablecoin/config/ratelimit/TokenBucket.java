package de.atruvia.stablecoin.config.ratelimit;

/**
 * Thread-sicherer Token-Bucket für API Rate Limiting.
 *
 * Refill-Strategie: kontinuierlich proportional zur verstrichenen Zeit.
 * Kapazität = Burst-Limit = refillTokens (einfache "leaky bucket"-Näherung).
 */
public final class TokenBucket {

    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodNanos;

    private long availableTokens;
    private long lastRefillNanos;

    private TokenBucket(long capacity, long refillTokens, long refillPeriodNanos) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = refillPeriodNanos;
        this.availableTokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    public static TokenBucket ofRequestsPerMinute(int requestsPerMinute) {
        return new TokenBucket(requestsPerMinute, requestsPerMinute, 60_000_000_000L);
    }

    public static TokenBucket ofRequestsPerSecond(int requestsPerSecond) {
        return new TokenBucket(requestsPerSecond, requestsPerSecond, 1_000_000_000L);
    }

    public synchronized boolean tryConsume() {
        refill();
        if (availableTokens > 0) {
            availableTokens--;
            return true;
        }
        return false;
    }

    private void refill() {
        long nowNanos = System.nanoTime();
        long elapsedNanos = nowNanos - lastRefillNanos;
        if (elapsedNanos >= refillPeriodNanos) {
            long periods = elapsedNanos / refillPeriodNanos;
            availableTokens = Math.min(capacity, availableTokens + periods * refillTokens);
            lastRefillNanos = nowNanos - (elapsedNanos % refillPeriodNanos);
        }
    }
}
