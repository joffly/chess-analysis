package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StockfishResult — testes adicionais")
class StockfishResultAdditionalTest {

    @Test
    @DisplayName("record com eval null e mateIn null")
    void record_bothNulls() {
        StockfishResult r = new StockfishResult(null, null, null, List.of());
        assertThat(r.eval()).isNull();
        assertThat(r.mateIn()).isNull();
        assertThat(r.bestMove()).isNull();
        assertThat(r.pv()).isEmpty();
    }

    @Test
    @DisplayName("record com eval positivo grande")
    void record_largePositiveEval() {
        StockfishResult r = new StockfishResult(100.5, null, "e2e4", List.of());
        assertThat(r.eval()).isEqualTo(100.5);
    }

    @Test
    @DisplayName("record com eval negativo grande")
    void record_largeNegativeEval() {
        StockfishResult r = new StockfishResult(-50.25, null, "e2e4", List.of());
        assertThat(r.eval()).isEqualTo(-50.25);
    }

    @Test
    @DisplayName("record com PV com muitos lances")
    void record_longPv() {
        List<String> pv = List.of("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6");
        StockfishResult r = new StockfishResult(0.3, null, "e2e4", pv);
        assertThat(r.pv()).hasSize(10);
    }

    @Test
    @DisplayName("record com mateIn zero (não é válido mas deve aceitar)")
    void record_mateInZero() {
        StockfishResult r = new StockfishResult(null, 0, "e2e4", List.of());
        assertThat(r.mateIn()).isEqualTo(0);
    }

    @ParameterizedTest
    @CsvSource({
        "0.5, 1, e2e4",
        "-0.5, -1, e2e4",
        "10.0, null, d1h5",
    })
    @DisplayName("record com vários valores")
    void record_variousValues(String eval, String mateIn, String bestMove) {
        Double evalDouble = eval.equals("null") ? null : Double.parseDouble(eval);
        Integer mateInInt = mateIn.equals("null") ? null : Integer.parseInt(mateIn);
        
        StockfishResult r = new StockfishResult(evalDouble, mateInInt, bestMove, List.of());
        assertThat(r.eval()).isEqualTo(evalDouble);
        assertThat(r.mateIn()).isEqualTo(mateInInt);
        assertThat(r.bestMove()).isEqualTo(bestMove);
    }

    @Test
    @DisplayName("record com bestMove vazio")
    void record_emptyBestMove() {
        StockfishResult r = new StockfishResult(0.5, null, "", List.of());
        assertThat(r.bestMove()).isEmpty();
    }

    @Test
    @DisplayName("empty() factory method")
    void empty_factoryMethod() {
        StockfishResult r = StockfishResult.empty();
        assertThat(r.eval()).isEqualTo(0.0);
        assertThat(r.mateIn()).isNull();
        assertThat(r.bestMove()).isNull();
        assertThat(r.pv()).isEmpty();
    }

    @Test
    @DisplayName("dois records com eval 0.0 são iguais")
    void record_zeroEvalEquality() {
        StockfishResult r1 = new StockfishResult(0.0, null, "e2e4", List.of());
        StockfishResult r2 = new StockfishResult(0.0, null, "e2e4", List.of());
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    @DisplayName("record com PV com um só lance")
    void record_singleMovePv() {
        StockfishResult r = new StockfishResult(0.5, null, "e2e4", List.of("e2e4"));
        assertThat(r.pv()).containsExactly("e2e4");
    }

    @Test
    @DisplayName("toString inclui mateIn")
    void record_toString_mateIn() {
        StockfishResult r = new StockfishResult(null, 3, "d1h5", List.of());
        String str = r.toString();
        assertThat(str).contains("3");
    }

    @Test
    @DisplayName("records com diferentes bestMove não são iguais")
    void record_inequality_differentBestMove() {
        StockfishResult r1 = new StockfishResult(0.5, null, "e2e4", List.of());
        StockfishResult r2 = new StockfishResult(0.5, null, "d2d4", List.of());
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    @DisplayName("records com diferentes PV não são iguais")
    void record_inequality_differentPv() {
        StockfishResult r1 = new StockfishResult(0.5, null, "e2e4", List.of("e7e5"));
        StockfishResult r2 = new StockfishResult(0.5, null, "e2e4", List.of("c7c5"));
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    @DisplayName("record com eval muito positivo")
    void record_veryLargePositiveEval() {
        StockfishResult r = new StockfishResult(999.99, null, "e2e4", List.of());
        assertThat(r.eval()).isEqualTo(999.99);
    }

    @Test
    @DisplayName("record com eval muito negativo")
    void record_veryLargeNegativeEval() {
        StockfishResult r = new StockfishResult(-999.99, null, "e2e4", List.of());
        assertThat(r.eval()).isEqualTo(-999.99);
    }
}
