package de.atruvia.stablecoin.kafka.dev;

import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent-Wrapper, der eine Kafka-Nachricht simuliert.
 * Im Dev-Profil ersetzt dieser Mechanismus den echten Kafka-Broker vollständig.
 */
public class KafkaSimulationEvent extends ApplicationEvent {

    private final String topic;
    private final String eventType;
    private final String jsonPayload;
    private final Object payload;

    public KafkaSimulationEvent(Object source, String topic, String eventType,
                                 String jsonPayload, Object payload) {
        super(source);
        this.topic = topic;
        this.eventType = eventType;
        this.jsonPayload = jsonPayload;
        this.payload = payload;
    }

    public String getTopic()       { return topic; }
    public String getEventType()   { return eventType; }
    public String getJsonPayload() { return jsonPayload; }
    public Object getPayload()     { return payload; }
}
