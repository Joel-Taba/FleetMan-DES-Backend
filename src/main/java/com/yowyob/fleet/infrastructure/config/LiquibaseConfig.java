package com.yowyob.fleet.infrastructure.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class LiquibaseConfig {

    @Value("${spring.liquibase.url}")
    private String url;
    
    @Value("${spring.liquibase.user}")
    private String username;
    
    @Value("${spring.liquibase.password}")
    private String password;

    @Value("${spring.liquibase.change-log}")
    private String changeLog;

    @Value("${spring.liquibase.default-schema}")
    private String defaultSchema;

    /**
     * Crée une DataSource JDBC classique uniquement pour Liquibase.
     * Indispensable car R2DBC ne peut pas gérer Liquibase.
     */
    @Bean
    public DataSource liquibaseDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled", matchIfMissing = true)
    public SpringLiquibase liquibase(DataSource liquibaseDataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(liquibaseDataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.setDefaultSchema(defaultSchema);
        // On force le contexte "local" ou "prod" selon besoin, ici on prend tout
        // liquibase.setContexts("local"); 
        
        System.out.println("🚀 FORCING LIQUIBASE EXECUTION ON SCHEMA: " + defaultSchema);
        
        return liquibase;
    }
}
