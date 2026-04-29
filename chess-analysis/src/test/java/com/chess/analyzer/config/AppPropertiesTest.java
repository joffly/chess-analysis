package com.chess.analyzer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppProperties — testes unitários")
class AppPropertiesTest {

    @Test
    @DisplayName("construtor com null stockfishPath usa string vazia")
    void constructor_nullStockfishPath() {
        AppProperties props = new AppProperties(null, 15, 5000, 4, "", 0);
        assertThat(props.stockfishPath()).isEmpty();
    }

    @Test
    @DisplayName("construtor com analysisDepth <= 0 usa valor padrão 15")
    void constructor_invalidAnalysisDepth() {
        AppProperties props1 = new AppProperties("path", 0, 5000, 4, "", 0);
        AppProperties props2 = new AppProperties("path", -10, 5000, 4, "", 0);
        
        assertThat(props1.analysisDepth()).isEqualTo(15);
        assertThat(props2.analysisDepth()).isEqualTo(15);
    }

    @Test
    @DisplayName("construtor com analysisTimeLimitMs <= 0 usa valor padrão 5000")
    void constructor_invalidAnalysisTimeLimit() {
        AppProperties props1 = new AppProperties("path", 15, 0, 4, "", 0);
        AppProperties props2 = new AppProperties("path", 15, -1000, 4, "", 0);
        
        assertThat(props1.analysisTimeLimitMs()).isEqualTo(5000);
        assertThat(props2.analysisTimeLimitMs()).isEqualTo(5000);
    }

    @Test
    @DisplayName("construtor com stockfishPoolSize <= 0 usa valor padrão 4")
    void constructor_invalidPoolSize() {
        AppProperties props1 = new AppProperties("path", 15, 5000, 0, "", 0);
        AppProperties props2 = new AppProperties("path", 15, 5000, -5, "", 0);
        
        assertThat(props1.stockfishPoolSize()).isEqualTo(4);
        assertThat(props2.stockfishPoolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("construtor com valores válidos mantém os valores")
    void constructor_validValues() {
        AppProperties props = new AppProperties("/path/to/stockfish", 20, 10000, 8, "", 0);
        
        assertThat(props.stockfishPath()).isEqualTo("/path/to/stockfish");
        assertThat(props.analysisDepth()).isEqualTo(20);
        assertThat(props.analysisTimeLimitMs()).isEqualTo(10000);
        assertThat(props.stockfishPoolSize()).isEqualTo(8);
    }

    @Test
    @DisplayName("construtor com stockfishPath válido")
    void constructor_validStockfishPath() {
        String path = "C:\\Program Files\\Stockfish\\stockfish.exe";
        AppProperties props = new AppProperties(path, 15, 5000, 4, "", 0);
        
        assertThat(props.stockfishPath()).isEqualTo(path);
    }

    @Test
    @DisplayName("construtor com analysisDepth positivo grande")
    void constructor_largeAnalysisDepth() {
        AppProperties props = new AppProperties("path", 100, 5000, 4, "", 0);
        
        assertThat(props.analysisDepth()).isEqualTo(100);
    }

    @Test
    @DisplayName("construtor com analysisTimeLimitMs grande")
    void constructor_largeTimeLimit() {
        AppProperties props = new AppProperties("path", 15, 60000, 4, "", 0);
        
        assertThat(props.analysisTimeLimitMs()).isEqualTo(60000);
    }

    @Test
    @DisplayName("construtor com stockfishPoolSize grande")
    void constructor_largePoolSize() {
        AppProperties props = new AppProperties("path", 15, 5000, 32, "", 0);
        
        assertThat(props.stockfishPoolSize()).isEqualTo(32);
    }

    @Test
    @DisplayName("todos os defaults aplicados simultaneamente")
    void constructor_allDefaults() {
        AppProperties props = new AppProperties(null, 0, 0, 0, null, 0);
        
        assertThat(props.stockfishPath()).isEmpty();
        assertThat(props.analysisDepth()).isEqualTo(15);
        assertThat(props.analysisTimeLimitMs()).isEqualTo(5000);
        assertThat(props.stockfishPoolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("dois records com mesmos valores são iguais")
    void record_equality() {
        AppProperties p1 = new AppProperties("path", 15, 5000, 4, "", 0);
        AppProperties p2 = new AppProperties("path", 15, 5000, 4, "", 0);

        assertThat(p1).isEqualTo(p2);
    }

    @Test
    @DisplayName("records diferentes não são iguais")
    void record_inequality() {
        AppProperties p1 = new AppProperties("path1", 15, 5000, 4, "", 0);
        AppProperties p2 = new AppProperties("path2", 15, 5000, 4, "", 0);
        
        assertThat(p1).isNotEqualTo(p2);
    }

    @Test
    @DisplayName("hashCode igual para records equivalentes")
    void record_hashCode() {
        AppProperties p1 = new AppProperties("path", 15, 5000, 4, "", 0);
        AppProperties p2 = new AppProperties("path", 15, 5000, 4, "", 0);

        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    @DisplayName("toString contém valores")
    void record_toString() {
        AppProperties props = new AppProperties("mypath", 15, 5000, 4, "", 0);
        String str = props.toString();
        
        assertThat(str).contains("mypath").contains("15");
    }

    @Test
    @DisplayName("analysisDepth = 1 é válido")
    void constructor_minValidDepth() {
        AppProperties props = new AppProperties("path", 1, 5000, 4, "", 0);
        assertThat(props.analysisDepth()).isEqualTo(1);
    }

    @Test
    @DisplayName("analysisTimeLimitMs = 1 é válido")
    void constructor_minValidTimeLimit() {
        AppProperties props = new AppProperties("path", 15, 1, 4, "", 0);
        assertThat(props.analysisTimeLimitMs()).isEqualTo(1);
    }

    @Test
    @DisplayName("stockfishPoolSize = 1 é válido")
    void constructor_minValidPoolSize() {
        AppProperties props = new AppProperties("path", 15, 5000, 1, "", 0);
        assertThat(props.stockfishPoolSize()).isEqualTo(1);
    }
}
