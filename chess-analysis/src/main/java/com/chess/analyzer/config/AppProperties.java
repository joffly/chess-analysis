package com.chess.analyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades configuráveis via application.properties (prefixo "chess").
 *
 * Exemplo:
 *   chess.stockfish-path=C:/stockfish/stockfish.exe
 *   chess.analysis-depth=15
 */
@ConfigurationProperties(prefix = "chess")
public record AppProperties(
        String stockfishPath,
        int    analysisDepth,
        int    analysisTimeLimitMs
) {
    public AppProperties {
        if (stockfishPath       == null) stockfishPath       = "";
        if (analysisDepth       <= 0)   analysisDepth       = 15;
        if (analysisTimeLimitMs <= 0)   analysisTimeLimitMs = 5000;
    }
}
