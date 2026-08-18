package de.atruvia.stablecoin.kafka.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Dev-only: Inspect simulierten Kafka-Event-Store via REST.
 *
 * GET /api/v1/dev/events                → alle Events (letzte 200)
 * GET /api/v1/dev/events?topic=xyz      → Events nach Topic gefiltert
 * DELETE /api/v1/dev/events             → Event-Store leeren
 *
 * Nur aktiv im dev-Profil.
 */
@RestController
@RequestMapping("/api/v1/dev/events")
@Profile("dev")
@ConditionalOnProperty(name = "app.security.dev-mode", havingValue = "true")
public class DevKafkaAdminController {

    private final InMemoryEventStore eventStore;

    public DevKafkaAdminController(InMemoryEventStore eventStore) {
        this.eventStore = eventStore;
    }

    @GetMapping
    public ResponseEntity<List<InMemoryEventStore.StoredEvent>> listEvents(
            @RequestParam(required = false) String topic) {
        List<InMemoryEventStore.StoredEvent> events = topic != null
                ? eventStore.getByTopic(topic)
                : eventStore.getAll();
        return ResponseEntity.ok(events);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Integer>> count() {
        return ResponseEntity.ok(Map.of("totalEvents", eventStore.size()));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        eventStore.clear();
        return ResponseEntity.noContent().build();
    }
}
