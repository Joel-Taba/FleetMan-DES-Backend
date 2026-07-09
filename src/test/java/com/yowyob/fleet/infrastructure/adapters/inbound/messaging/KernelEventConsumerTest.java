package com.yowyob.fleet.infrastructure.adapters.inbound.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KernelEventConsumerTest {

    @Test
    void kernelBusinessEvent_recordHoldsTypeAndEntityId() {
        var event = new KernelEventConsumer.KernelBusinessEvent(
                "ORGANIZATION_SUSPENDED",
                "5e69f5c5-1f03-41cb-a4a3-d59188f73323",
                java.util.Map.of("reason", "test"));

        assertThat(event.type()).isEqualTo("ORGANIZATION_SUSPENDED");
        assertThat(event.entityId()).contains("5e69f5c5");
        assertThat(event.payload()).containsEntry("reason", "test");
    }
}
