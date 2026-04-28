package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do record StockfishResult.
 */
@DisplayName("StockfishResult — testes unitários")
class StockfishResultTest {

    @Test
    @DisplayName("Record com eval numérico armazena todos os campos")
    void record_withEval_storesAllFields() {
        StockfishResult r = new StockfishResult(0.42, null, "e2e4", List.of("e2e4", "e7e5"));
        assertThat(r.eval()).isEqualTo(0.42);
        assertThat(r.mateIn()).isNull();
        assertThat(r.bestMove()).isEqualTo("e2e4");
        assertThat(r.pv()).containsExactly("e2e4", "e7e5");
    }

    @Test
    @DisplayName("Record com mateIn positivo armazena todos os campos")
    void record_withMateIn_storesAllFields() {
        StockfishResult r = new StockfishResult(null, 2, "d1h5", List.of("d1h5", "e8e7"));
        assertThat(r.eval()).isNull();
        assertThat(r.mateIn()).isEqualTo(2);
        assertThat(r.bestMove()).isEqualTo("d1h5");
        assertThat(r.pv()).hasSize(2);
    }

    @Test
    @DisplayName("Record com mateIn negativo (sendo mateado) é armazenado")
    void record_withNegativeMateIn_isStored() {
        StockfishResult r = new StockfishResult(null, -3, "e1e2", List.of());
        assertThat(r.mateIn()).isEqualTo(-3);
    }

    @Test
    @DisplayName("Record com PV vazio não lança exceção")
    void record_emptyPv_isValid() {
        StockfishResult r = new StockfishResult(0.0, null, "e2e4", List.of());
        assertThat(r.pv()).isEmpty();
    }

    @Test
    @DisplayName("Dois records com mesmos valores são iguais (equals/hashCode do record)")
    void record_equalityByValue() {
        List<String> pv = List.of("e2e4");
        StockfishResult r1 = new StockfishResult(0.5, null, "e2e4", pv);
        StockfishResult r2 = new StockfishResult(0.5, null, "e2e4", pv);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("toString do record inclui os valores dos campos")
    void record_toString_containsFieldValues() {
        StockfishResult r = new StockfishResult(1.23, null, "d2d4", List.of());
        String str = r.toString();
        assertThat(str).contains("1.23");
        assertThat(str).contains("d2d4");
    }
}
