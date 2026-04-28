package com.chess.analyzer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.chess.analyzer.util.LichessBlunderClassifier.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LichessBlunderClassifier — testes unitários")
class LichessBlunderClassifierTest {

    @Test
    @DisplayName("winningChances(0) retorna 0 (posição neutra)")
    void winningChances_zero_returnsZero() {
        double result = winningChances(0);
        assertThat(result).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("winningChances(1000) retorna valor positivo próximo a 1")
    void winningChances_1000_returnsNearOne() {
        double result = winningChances(1000);
        assertThat(result).isGreaterThan(0.9).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("winningChances(-1000) retorna valor negativo próximo a -1")
    void winningChances_minus1000_returnsNearMinusOne() {
        double result = winningChances(-1000);
        assertThat(result).isLessThan(-0.9).isGreaterThanOrEqualTo(-1.0);
    }

    @Test
    @DisplayName("winningChances com valor > 1000 é limitado a 1000")
    void winningChances_capped_atPositive1000() {
        double result1 = winningChances(2000);
        double result2 = winningChances(1000);
        assertThat(result1).isEqualTo(result2);
    }

    @Test
    @DisplayName("winningChances com valor < -1000 é limitado a -1000")
    void winningChances_capped_atNegative1000() {
        double result1 = winningChances(-5000);
        double result2 = winningChances(-1000);
        assertThat(result1).isEqualTo(result2);
    }

    @Test
    @DisplayName("winningChances retorna valor no intervalo [-1, 1]")
    void winningChances_alwaysInRange() {
        for (int cp = -2000; cp <= 2000; cp += 250) {
            double result = winningChances(cp);
            assertThat(result).isGreaterThanOrEqualTo(-1.0).isLessThanOrEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("classify: boa jogada das brancas (eval melhora)")
    void classify_goodMoveWhite_returnsGood() {
        Judgement j = classify(0, -0.5, true);
        assertThat(j).isEqualTo(Judgement.GOOD);
    }

    @Test
    @DisplayName("classify: boa jogada das pretas (eval piora do ponto de vista das brancas)")
    void classify_goodMoveBlack_returnsGood() {
        Judgement j = classify(0, 0.5, false);
        assertThat(j).isEqualTo(Judgement.GOOD);
    }

    @Test
    @DisplayName("classify: lance das brancas que causa inaccuracy (delta ~0.10)")
    void classify_inaccuracy_returnsInaccuracy() {
        Judgement j = classify(-100, 100, true);
        assertThat(j).isEqualTo(Judgement.INACCURACY);
    }

    @Test
    @DisplayName("classify: lance das brancas que causa mistake (delta ~0.20)")
    void classify_mistake_returnsMistake() {
        Judgement j = classify(-200, 200, true);
        assertThat(j).isEqualTo(Judgement.MISTAKE);
    }

    @Test
    @DisplayName("classify: lance das brancas que causa blunder (delta >= 0.30)")
    void classify_blunder_returnsBlunder() {
        Judgement j = classify(-400, 200, true);
        assertThat(j).isEqualTo(Judgement.BLUNDER);
    }

    @Test
    @DisplayName("classify: lance das pretas que causa blunder")
    void classify_blunderBlack_returnsBlunder() {
        Judgement j = classify(-100, -500, false);
        assertThat(j).isEqualTo(Judgement.BLUNDER);
    }

    @Test
    @DisplayName("isBlunder retorna true quando classificação é BLUNDER")
    void isBlunder_returnsTrue() {
        boolean result = isBlunder(-400, 200, true);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isBlunder retorna false quando classificação não é BLUNDER")
    void isBlunder_returnsFalse() {
        boolean result = isBlunder(-100, 100, true);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("classifyMateLost(1000) retorna INACCURACY")
    void classifyMateLost_1000_returnsInaccuracy() {
        Judgement j = classifyMateLost(1000);
        assertThat(j).isEqualTo(Judgement.INACCURACY);
    }

    @Test
    @DisplayName("classifyMateLost(800) retorna MISTAKE")
    void classifyMateLost_800_returnsMistake() {
        Judgement j = classifyMateLost(800);
        assertThat(j).isEqualTo(Judgement.MISTAKE);
    }

    @Test
    @DisplayName("classifyMateLost(500) retorna BLUNDER")
    void classifyMateLost_500_returnsBlunder() {
        Judgement j = classifyMateLost(500);
        assertThat(j).isEqualTo(Judgement.BLUNDER);
    }

    @Test
    @DisplayName("classifyMateLost com valor > 999 é INACCURACY")
    void classifyMateLost_gt999_isInaccuracy() {
        assertThat(classifyMateLost(1001)).isEqualTo(Judgement.INACCURACY);
        assertThat(classifyMateLost(2000)).isEqualTo(Judgement.INACCURACY);
    }

    @Test
    @DisplayName("classifyMateLost com valor entre 700 e 999 é MISTAKE")
    void classifyMateLost_between700and999_isMistake() {
        assertThat(classifyMateLost(701)).isEqualTo(Judgement.MISTAKE);
        assertThat(classifyMateLost(999)).isEqualTo(Judgement.MISTAKE);
    }

    @Test
    @DisplayName("classifyMateLost com valor <= 700 é BLUNDER")
    void classifyMateLost_le700_isBlunder() {
        assertThat(classifyMateLost(700)).isEqualTo(Judgement.BLUNDER);
        assertThat(classifyMateLost(0)).isEqualTo(Judgement.BLUNDER);
        assertThat(classifyMateLost(-500)).isEqualTo(Judgement.BLUNDER);
    }

    @Test
    @DisplayName("classifyMateCreated(-1000) retorna INACCURACY")
    void classifyMateCreated_minus1000_returnsInaccuracy() {
        Judgement j = classifyMateCreated(-1000);
        assertThat(j).isEqualTo(Judgement.INACCURACY);
    }

    @Test
    @DisplayName("classifyMateCreated(-800) retorna MISTAKE")
    void classifyMateCreated_minus800_returnsMistake() {
        Judgement j = classifyMateCreated(-800);
        assertThat(j).isEqualTo(Judgement.MISTAKE);
    }

    @Test
    @DisplayName("classifyMateCreated(-500) retorna BLUNDER")
    void classifyMateCreated_minus500_returnsBlunder() {
        Judgement j = classifyMateCreated(-500);
        assertThat(j).isEqualTo(Judgement.BLUNDER);
    }

    @Test
    @DisplayName("classifyMateCreated com valor < -999 é INACCURACY")
    void classifyMateCreated_lt_minus999_isInaccuracy() {
        assertThat(classifyMateCreated(-1001)).isEqualTo(Judgement.INACCURACY);
        assertThat(classifyMateCreated(-2000)).isEqualTo(Judgement.INACCURACY);
    }

    @Test
    @DisplayName("classifyMateCreated com valor entre -999 e -700 é MISTAKE")
    void classifyMateCreated_between_minus999_and_minus700_isMistake() {
        assertThat(classifyMateCreated(-701)).isEqualTo(Judgement.MISTAKE);
        assertThat(classifyMateCreated(-999)).isEqualTo(Judgement.MISTAKE);
    }

    @Test
    @DisplayName("classifyMateCreated com valor >= -700 é BLUNDER")
    void classifyMateCreated_ge_minus700_isBlunder() {
        assertThat(classifyMateCreated(-700)).isEqualTo(Judgement.BLUNDER);
        assertThat(classifyMateCreated(0)).isEqualTo(Judgement.BLUNDER);
        assertThat(classifyMateCreated(500)).isEqualTo(Judgement.BLUNDER);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, GOOD",
        "50, 50, GOOD",
        "-50, -50, GOOD",
        "100, 0, INACCURACY",
        "150, 0, MISTAKE",
        "300, 0, BLUNDER",
    })
    @DisplayName("classify com vários deltas (brancas)")
    void classify_variousDeltasWhite(double cpBefore, double cpAfter, String expectedJudgement) {
        Judgement j = classify(cpBefore, cpAfter, true);
        assertThat(j).isEqualTo(Judgement.valueOf(expectedJudgement));
    }

    @Test
    @DisplayName("Limiares exatos: INACCURACY_THRESHOLD = 0.10")
    void thresholds_inaccuracyThreshold() {
        assertThat(INACCURACY_THRESHOLD).isEqualTo(0.10);
    }

    @Test
    @DisplayName("Limiares exatos: MISTAKE_THRESHOLD = 0.20")
    void thresholds_mistakeThreshold() {
        assertThat(MISTAKE_THRESHOLD).isEqualTo(0.20);
    }

    @Test
    @DisplayName("Limiares exatos: BLUNDER_THRESHOLD = 0.30")
    void thresholds_blunderThreshold() {
        assertThat(BLUNDER_THRESHOLD).isEqualTo(0.30);
    }

    @Test
    @DisplayName("Simetria: delta positivo para brancas = delta negativo para pretas")
    void symmetry_whiteAndBlackPerspective() {
        Judgement white = classify(100, 0, true);
        Judgement black = classify(0, 100, false);
        assertThat(white).isEqualTo(black);
    }

    @Test
    @DisplayName("Limite inferior do BLUNDER_THRESHOLD exato")
    void classify_blunderThreshold_exact() {
        Judgement jBefore = classify(-300, 0, true);
        Judgement jAfter = classify(-299, 0, true);
        assertThat(jBefore).isEqualTo(Judgement.BLUNDER);
        assertThat(jAfter).isEqualTo(Judgement.MISTAKE);
    }
}
