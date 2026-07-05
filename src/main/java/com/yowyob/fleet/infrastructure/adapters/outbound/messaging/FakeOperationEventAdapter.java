package com.yowyob.fleet.infrastructure.adapters.outbound.messaging;

import com.yowyob.fleet.domain.ports.out.OperationEventPort;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Simulateur du bus d'événements opérationnels (Kafka).
 * Activé par : application.kafka.mode=fake (ou absence de Kafka).
 * Logge les événements au lieu de les publier sur Kafka.
 */
@Slf4j
public class FakeOperationEventAdapter implements OperationEventPort {

    @Override
    public Mono<Void> publishMaintenanceCreated(MaintenanceCreatedEvent event) {
        log.info("🛠 [FAKE EVENT] MaintenanceCreated — maintenance={} vehicle={} manager={}",
                event.maintenanceId(), event.vehicleId(), event.fleetManagerId());
        return Mono.empty();
    }

    @Override
    public Mono<Void> publishIncidentReported(IncidentReportedEvent event) {
        log.info("🛠 [FAKE EVENT] IncidentReported — incident={} type={} severity={} critical={}",
                event.incidentId(), event.incidentType(), event.severity(), event.isCritical());
        return Mono.empty();
    }
}
