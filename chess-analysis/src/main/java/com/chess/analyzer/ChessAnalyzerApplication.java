package com.chess.analyzer;

import com.chess.analyzer.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling   // habilita @Scheduled (job batch de sincronização Lichess)
public class ChessAnalyzerApplication {

    public static void main(String[] args) {
        // Virtual threads do Java 21 (Project Loom) para o Tomcat e análise I/O-bound
        System.setProperty("spring.threads.virtual.enabled", "true");
        SpringApplication.run(ChessAnalyzerApplication.class, args);
    }
}
