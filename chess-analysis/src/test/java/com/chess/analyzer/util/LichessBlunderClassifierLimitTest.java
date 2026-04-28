package com.chess.analyzer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.chess.analyzer.util.LichessBlunderClassifier.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LichessBlunderClassifier — testes de limites")
class LichessBlunderClassifierLimitTest {

    @Test
    @DisplayName("winningChances com CP muito grande (>10000)")
    void winningChances_extremelyLargeCP() {
        double r1 = winningChances(100000);
        double r2 = winningChances(1000);
        
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    @DisplayName("winningChances com CP muito pequeno (<-10000)")
    void winningChances_extremelySmallCP() {
        double r1 = winningChances(-100000);
        double r2 = winningChances(-1000);
        
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    @DisplayName("classify com delta exatamente 0.30 é BLUNDER")
    void classify_deltaExactly030() {
        Judgement j = classify(0, 0.30 * 100, true);
        assertThat(j).isEqualTo(Judgement.BLUNDER);
    }

    @Test
    @DisplayName("classify com delta 0.299 é MISTAKE")
    void classify_delta0299() {
        Judgement j = classify(0, 0.299 * 100, true);
        assertThat(j).isEqualTo(Judgement.MISTAKE);
    }

    @Test
    @DisplayName("classify com delta 0.20 é MISTAKE")
    void classify_deltaExactly020() {
        Judgement j = classify(0, 0.20 * 100, true);
        assertThat(j).isEqualTo(Judgement.MISTAKE);
    }

    @Test
    @DisplayName("classify com delta 0.199 é INACCURACY")
    void classify_delta0199() {
        Judgement j = classify(0, 0.199 * 100, true);
        assertThat(j).isEqualTo(Judgement.INACCURACY);
    }

    @Test
    @DisplayName("classify com delta 0.10 é INACCURACY")
    void classify_deltaExactly010() {
        Judgement j = classify(0, 0.10 * 100, true);
        assertThat(j).isEqualTo(Judgement.INACCURACY);
    }

    @Test
    @DisplayName("classify com delta 0.099 é GOOD")
    void classify_delta0099() {
        Judgement j = classify(0, 0.099 * 100, true);
        assertThat(j).isEqualTo(Judgement.GOOD);
    }

    @Test
    @DisplayName("isBlunder com delta exatamente 0.30")
    void isBlunder_deltaExactly030() {
        boolean result = isBlunder(0, 0.30 * 100, true);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isBlunder com delta 0.299")
    void isBlunder_delta0299() {
        boolean result = isBlunder(0, 0.299 * 100, true);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("classifyMateLost com valores limítrofes")
    void classifyMateLost_boundaries() {
        assertThat(classifyMateLost(1001)).isEqualTo(Judgement.INACCURACY);
        assertThat(classifyMateLost(1000)).isEqualTo(Judgement.INACCURACY);
        assertThat(classifyMateLost(999)).isEqualTo(Judgement.INACCURACY);
        assertThat(classifyMateLost(701)).isEqualTo(Judgement.MISTAKE);
        assertThat(classifyMateLost(700)).isEqualTo(Judgement.BLUNDER);
    }

    @Test
    @DisplayName("classifyMateCreated com valores limítrofes")
    void classifyMateCreated_boundaries() {
        assertThat(classifyMateCreated(-1001)).isEqualTo(Judgement.INACCURACY);
        assertThat(classifyMateCreated(-1000)).isEqualTo(Judgement.INACCURACY);
        assertThat(classifyMateCreated(-999)).isEqualTo(Judgement.INACCURACY);
        assertThat(classifyMateCreated(-701)).isEqualTo(Judgement.MISTAKE);
        assertThat(classifyMateCreated(-700)).isEqualTo(Judgement.BLUNDER);
    }

    @Test
    @DisplayName("winningChances com zero retorna próximo de 0")
    void winningChances_zeroNearZero() {
        double wc = winningChances(0);
        assertThat(wc).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.02));
    }

    @Test
    @DisplayName("winningChances monótona crescente")
    void winningChances_monotonic() {
        double wc1 = winningChances(100);
        double wc2 = winningChances(200);
        double wc3 = winningChances(300);
        
        assertThat(wc1).isLessThan(wc2);
        assertThat(wc2).isLessThan(wc3);
    }

    @Test
    @DisplayName("classify com brancas e pretas simétrico")
    void classify_symmetry() {
        Judgement jWhite = classify(200, 0, true);
        Judgement jBlack = classify(0, 200, false);
        
        assertThat(jWhite).isEqualTo(jBlack);
    }

    @Test
    @DisplayName("classifyMateLost com mate em 10 (eval muito positivo)")
    void classifyMateLost_mateAlmostWon() {
        Judgement j = classifyMateLost(1500);
        assertThat(j).isEqualTo(Judgement.INACCURACY);
    }

    @Test
    @DisplayName("classifyMateCreated com eval muito negativo")
    void classifyMateCreated_almostLost() {
        Judgement j = classifyMateCreated(-2000);
        assertThat(j).isEqualTo(Judgement.INACCURACY);
    }
}
