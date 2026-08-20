package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.entity.StablecoinCurrency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Dynamisches Routing-System für Token-Adapter.
 *
 * Registriert beim Start alle {@link StablecoinTokenAdapter}-Beans und baut
 * eine Dispatch-Map currency → adapter auf. Das Routing ist erweiterbar ohne
 * Code-Änderungen: neuer Adapter-Bean genügt.
 */
@Component
public class TokenAdapterRouter {

    private static final Logger log = LoggerFactory.getLogger(TokenAdapterRouter.class);

    private final Map<StablecoinCurrency, StablecoinTokenAdapter> registry;

    public TokenAdapterRouter(List<StablecoinTokenAdapter> adapters) {
        registry = adapters.stream()
                .flatMap(adapter -> adapter.supportedCurrencies().stream()
                        .map(currency -> Map.entry(currency, adapter)))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        log.info("[TOKEN-ROUTER] Adapter registriert: {}",
                registry.entrySet().stream()
                        .map(e -> e.getKey() + "→" + e.getValue().getAdapterName())
                        .collect(Collectors.joining(", ")));
    }

    /**
     * Gibt den Adapter für die angegebene Währung zurück.
     *
     * @throws NoSuchElementException wenn kein Adapter für die Währung registriert ist
     */
    public StablecoinTokenAdapter getAdapter(StablecoinCurrency currency) {
        StablecoinTokenAdapter adapter = registry.get(currency);
        if (adapter == null) {
            throw new NoSuchElementException("Kein Token-Adapter für Währung registriert: " + currency);
        }
        return adapter;
    }

    /** Gibt alle registrierten Currency-Adapter-Paare zurück (für Healthchecks/Admin). */
    public Map<StablecoinCurrency, String> getRegisteredAdapters() {
        return registry.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getAdapterName()));
    }
}
