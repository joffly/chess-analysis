package com.chess.analyzer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StockfishConfig — testes unitários")
class StockfishConfigTest {

    @Test
    @DisplayName("record com todos os campos armazena corretamente")
    void record_allFieldsStored() {
        StockfishConfig config = new StockfishConfig("/path/to/stockfish", 20, 10000L);
        
        assertThat(config.path()).isEqualTo("/path/to/stockfish");
        assertThat(config.depth()).isEqualTo(20);
        assertThat(config.timeLimitMs()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("record com path vazio")
    void record_emptyPath() {
        StockfishConfig config = new StockfishConfig("", 15, 5000L);
        
        assertThat(config.path()).isEmpty();
    }

    @Test
    @DisplayName("record com null path")
    void record_nullPath() {
        StockfishConfig config = new StockfishConfig(null, 15, 5000L);
        
        assertThat(config.path()).isNull();
    }

    @Test
    @DisplayName("record com depth 0")
    void record_depthZero() {
        StockfishConfig config = new StockfishConfig("path", 0, 5000L);
        
        assertThat(config.depth()).isZero();
    }

    @Test
    @DisplayName("record com depth negativo")
    void record_negativeDepth() {
        StockfishConfig config = new StockfishConfig("path", -5, 5000L);
        
        assertThat(config.depth()).isEqualTo(-5);
    }

    @Test
    @DisplayName("record com timeLimitMs 0")
    void record_timeLimitZero() {
        StockfishConfig config = new StockfishConfig("path", 15, 0L);
        
        assertThat(config.timeLimitMs()).isZero();
    }

    @Test
    @DisplayName("record com timeLimitMs negativo")
    void record_negativeTimeLimit() {
        StockfishConfig config = new StockfishConfig("path", 15, -1000L);
        
        assertThat(config.timeLimitMs()).isEqualTo(-1000L);
    }

    @Test
    @DisplayName("dois records com mesmos valores são iguais")
    void record_equality() {
        StockfishConfig c1 = new StockfishConfig("path", 15, 5000L);
        StockfishConfig c2 = new StockfishConfig("path", 15, 5000L);
        
        assertThat(c1).isEqualTo(c2);
    }

    @Test
    @DisplayName("records diferentes não são iguais")
    void record_inequality() {
        StockfishConfig c1 = new StockfishConfig("path1", 15, 5000L);
        StockfishConfig c2 = new StockfishConfig("path2", 15, 5000L);
        
        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    @DisplayName("hashCode igual para records equivalentes")
    void record_hashCodeEquality() {
        StockfishConfig c1 = new StockfishConfig("path", 15, 5000L);
        StockfishConfig c2 = new StockfishConfig("path", 15, 5000L);
        
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    }

    @Test
    @DisplayName("toString contém valores dos campos")
    void record_toString() {
        StockfishConfig config = new StockfishConfig("stockfish", 20, 10000L);
        String str = config.toString();
        
        assertThat(str).contains("stockfish").contains("20");
    }

    @Test
    @DisplayName("record com depth muito grande")
    void record_largeDepth() {
        StockfishConfig config = new StockfishConfig("path", 100, 5000L);
        
        assertThat(config.depth()).isEqualTo(100);
    }

    @Test
    @DisplayName("record com timeLimitMs muito grande")
    void record_largeTimeLimit() {
        StockfishConfig config = new StockfishConfig("path", 15, 1000000L);
        
        assertThat(config.timeLimitMs()).isEqualTo(1000000L);
    }

    @Test
    @DisplayName("record com Windows path")
    void record_windowsPath() {
        String winPath = "C:\\Program Files\\Stockfish\\stockfish.exe";
        StockfishConfig config = new StockfishConfig(winPath, 15, 5000L);
        
        assertThat(config.path()).isEqualTo(winPath);
    }

    @Test
    @DisplayName("record com Linux path")
    void record_linuxPath() {
        String linuxPath = "/usr/bin/stockfish";
        StockfishConfig config = new StockfishConfig(linuxPath, 15, 5000L);
        
        assertThat(config.path()).isEqualTo(linuxPath);
    }

    @Test
    @DisplayName("record com path com espaços")
    void record_pathWithSpaces() {
        String path = "/path with spaces/stockfish";
        StockfishConfig config = new StockfishConfig(path, 15, 5000L);
        
        assertThat(config.path()).isEqualTo(path);
    }

    @Test
    @DisplayName("record com todos os valores mínimos")
    void record_minValues() {
        StockfishConfig config = new StockfishConfig("", 0, 0L);
        
        assertThat(config.path()).isEmpty();
        assertThat(config.depth()).isZero();
        assertThat(config.timeLimitMs()).isZero();
    }
}
