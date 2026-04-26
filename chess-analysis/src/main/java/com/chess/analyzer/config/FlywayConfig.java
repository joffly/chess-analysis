package com.chess.analyzer.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Configuração explícita do Flyway.
 *
 * O Spring Boot 4 não garante o auto-wiring do Flyway ao DataSource
 * em todos os cenários. Esta classe garante que a migração seja
 * executada antes do Hibernate validar o schema.
 */
@Configuration
@EnableConfigurationProperties(FlywayProperties.class)
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource, FlywayProperties props) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(props.getLocations().toArray(String[]::new))
                .validateOnMigrate(props.isValidateOnMigrate())
                .outOfOrder(props.isOutOfOrder())
                .baselineOnMigrate(props.isBaselineOnMigrate())
                .load();
    }
}
