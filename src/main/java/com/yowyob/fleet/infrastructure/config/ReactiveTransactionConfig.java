package com.yowyob.fleet.infrastructure.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

@Configuration
public class ReactiveTransactionConfig {

    /**
     * Définit le TransactionManager R2DBC comme étant le principal.
     * Cela résout le conflit avec le DataSourceTransactionManager (JDBC) apporté par Liquibase.
     */
    @Bean
    @Primary
    public ReactiveTransactionManager connectionFactoryTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
}