package de.atruvia.stablecoin.kafka.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Thread-sicherer In-Memory-Event-Store für die Dev-Kafka-Simulation.
 * Hält die letzten 200 Ereignisse pro Topic für Dev-Inspektion.
 *
 * Zugänglich über: GET /api/v1/dev/events
 */
@Component
@Profile("dev")
public class InMemoryEventStore {

    private static final int MAX_EVENTS = 200;

    private final LinkedList<StoredEvent> events = new LinkedList<>();

    public record StoredEvent(
            String topic,
            String eventType,
            String jsonPayload,
            Instant receivedAt
    ) {}

    public synchronized void add(String topic, String eventType, String jsonPayload) {
        events.addFirst(new StoredEvent(topic, eventType, jsonPayload, Instant.now()));
        if (events.size() > MAX_EVENTS) {
            events.removeLast();
        }
    }

    public synchronized List<StoredEvent> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized List<StoredEvent> getByTopic(String topic) {
        return events.stream()
                .filter(e -> e.topic().equals(topic))
                .toList();
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized void clear() {
        events.clear();
    }
}
