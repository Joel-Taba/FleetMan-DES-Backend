package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.OperationEventPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.messaging.FakeOperationEventAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.messaging.KafkaOperationEventAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;

@Configuration
public class OperationEventConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "application.kafka.mode", havingValue = "fake", matchIfMissing = true)
    public OperationEventPort fakeOperationEventPort() {
        return new FakeOperationEventAdapter();
    }

    @Bean
    @ConditionalOnProperty(
            name = "application.kafka.mode",
            havingValue = "real",
            matchIfMissing = false
    )
    public OperationEventPort kafkaOperationEventPort(
            ReactiveKafkaProducerTemplate<String, Object> kafkaProducer
    ) {
        return new KafkaOperationEventAdapter(kafkaProducer);
    }
}
