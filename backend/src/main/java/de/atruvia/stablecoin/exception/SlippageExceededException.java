package de.atruvia.stablecoin.exception;

/**
 * G-06: Kursabweichung (Slippage) zwischen Auftragserteilung und Blockchain-Settlement
 * überschreitet die in TenantSettings konfigurierte Toleranz.
 * → HTTP 422 UNPROCESSABLE_ENTITY (BIZ_005)
 */
public class SlippageExceededException extends RuntimeException {
    private final int actualBps;
    private final int limitBps;

    public SlippageExceededException(int actualBps, int limitBps) {
        super(String.format("Kursabweichung %d BPS überschreitet Slippage-Limit %d BPS", actualBps, limitBps));
        this.actualBps = actualBps;
        this.limitBps  = limitBps;
    }

    public int getActualBps() { return actualBps; }
    public int getLimitBps()  { return limitBps; }
}
