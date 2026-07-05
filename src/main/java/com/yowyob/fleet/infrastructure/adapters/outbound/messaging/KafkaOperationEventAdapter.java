package com.yowyob.fleet.infrastructure.adapters.outbound.messaging;

import com.yowyob.fleet.domain.ports.out.OperationEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import reactor.core.publisher.Mono;

/**
 * Adapter événementiel : publie les événements du module Opérations vers Kafka.
 * Implémente OperationEventPort.
 *
 * En cas d'échec Kafka, l'erreur est loggée mais ne propage pas d'exception
 * (les services appellent ce port en fire & forget).
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.kafka.mode", havingValue = "real")
public class KafkaOperationEventAdapter implements OperationEventPort {

    private final ReactiveKafkaProducerTemplate<String, Object> kafkaProducer;

    @Value("${application.kafka.topics.operation-events:operation-events-topic}")
    private String operationEventsTopic;

    @Override
    public Mono<Void> publishMaintenanceCreated(MaintenanceCreatedEvent event) {
        log.info("📤 Publication événement MaintenanceCreated — vehicleId: {}, managerId: {}",
                event.vehicleId(), event.fleetManagerId());

        return kafkaProducer.send(operationEventsTopic, event.maintenanceId().toString(), event)
                .doOnSuccess(result -> log.debug("✅ MaintenanceCreatedEvent envoyé — offset: {}",
                        result.recordMetadata().offset()))
                .doOnError(e -> log.error("❌ Échec envoi MaintenanceCreatedEvent: {}", e.getMessage()))
                .then();
    }

    @Override
    public Mono<Void> publishIncidentReported(IncidentReportedEvent event) {
        String logLevel = event.isCritical() ? "🚨 CRITIQUE" : "📤";
        log.info("{} Publication événement IncidentReported — type: {}, severity: {}, vehicleId: {}",
                logLevel, event.incidentType(), event.severity(), event.vehicleId());

        return kafkaProducer.send(operationEventsTopic, event.incidentId().toString(), event)
                .doOnSuccess(result -> log.debug("✅ IncidentReportedEvent envoyé — offset: {}",
                        result.recordMetadata().offset()))
                .doOnError(e -> log.error("❌ Échec envoi IncidentReportedEvent: {}", e.getMessage()))
                .then();
    }
}
