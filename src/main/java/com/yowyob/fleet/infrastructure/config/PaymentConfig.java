package com.yowyob.fleet.infrastructure.config;

import com.yowyob.fleet.domain.ports.out.ExternalPaymentPort;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.FakePaymentAdapter;
import com.yowyob.fleet.infrastructure.adapters.outbound.external.PaymentApiAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class PaymentConfig {

    @Bean
    @ConditionalOnProperty(name = "application.external.payment-mode", havingValue = "fake")
    public ExternalPaymentPort fakePaymentPort() {
        return new FakePaymentAdapter();
    }

    @Bean
    @ConditionalOnProperty(
            name = "application.external.payment-mode",
            havingValue = "remote",
            matchIfMissing = true
    )
    public ExternalPaymentPort realPaymentPort(
            @Qualifier("paymentWebClient") WebClient paymentWebClient
    ) {
        return new PaymentApiAdapter(paymentWebClient);
    }
}
