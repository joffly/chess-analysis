package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura complementar de MoveEntry — cenários não cobertos em MoveEntryTest:
 * lance das pretas, eval zero, sobrescrita de análise e PV nulo.
 */
@DisplayName("MoveEntry — testes extras")
class MoveEntryExtraTest {

    private static final String FEN1 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
    private static final String FEN2 =
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2";

    @Test
    @DisplayName("isWhiteTurn false para lance das pretas")
    void isWhiteTurn_false_forBlackMove() {
        MoveEntry entry = new MoveEntry("e7e5", "e5", FEN1, FEN2, 1, false);
        assertThat(entry.isWhiteTurn()).isFalse();
    }

    @Test
    @DisplayName("getEvalFormatted retorna '+0.00' para eval zero")
    void evalFormatted_zero_returnsPlusZero() {
        MoveEntry entry = new MoveEntry("e7e5", "e5", FEN1, FEN2, 1, false);
        entry.setAnalysis(0.0, null, "e2e4", List.of());
        assertThat(entry.getEvalFormatted()).isEqualTo("+0.00");
    }

    @Test
    @DisplayName("Sobrescrita de análise reflete o último valor")
    void setAnalysis_overwrite_reflectsLatestValue() {
        MoveEntry entry = new MoveEntry("e7e5", "e5", FEN1, FEN2, 1, false);
        entry.setAnalysis(1.0, null, "e2e4", List.of());
        entry.setAnalysis(-0.5, null, "d2d4", List.of("d2d4", "d7d5"));

        assertThat(entry.getEval()).isEqualTo(-0.5);
        assertThat(entry.getBestMove()).isEqualTo("d2d4");
        assertThat(entry.getPv()).containsExactly("d2d4", "d7d5");
        assertThat(entry.getEvalFormatted()).isEqualTo("-0.50");
    }

    @Test
    @DisplayName("PV vazio é armazenado sem exceção")
    void setAnalysis_emptyPv_isStored() {
        MoveEntry entry = new MoveEntry("e7e5", "e5", FEN1, FEN2, 1, false);
        entry.setAnalysis(0.3, null, "e2e4", List.of());
        assertThat(entry.getPv()).isEmpty();
    }

    @Test
    @DisplayName("moveNumber 2 é armazenado corretamente")
    void moveNumber_secondFullMove_isStored() {
        MoveEntry entry = new MoveEntry("g1f3", "Nf3", FEN1, FEN2, 2, true);
        assertThat(entry.getMoveNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("getEvalFormatted com eval negativo grande retorna representação correta")
    void evalFormatted_largeNegative_isCorrect() {
        MoveEntry entry = new MoveEntry("e7e5", "e5", FEN1, FEN2, 1, false);
        entry.setAnalysis(-9.99, null, "e2e4", List.of());
        assertThat(entry.getEvalFormatted()).isEqualTo("-9.99");
    }

    @Test
    @DisplayName("setAnalysis com PV de um único lance funciona")
    void setAnalysis_singleMovePv_isStored() {
        MoveEntry entry = new MoveEntry("e7e5", "e5", FEN1, FEN2, 1, false);
        entry.setAnalysis(0.5, null, "e2e4", List.of("e2e4"));
        assertThat(entry.getPv()).containsExactly("e2e4");
    }
}
