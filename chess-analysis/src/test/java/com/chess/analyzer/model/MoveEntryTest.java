package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoveEntry — testes unitários")
class MoveEntryTest {

    private static final String FEN1 =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String FEN2 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Test
    @DisplayName("getEvalFormatted retorna '?' antes da análise")
    void evalFormatted_notAnalyzed_returnsQuestionMark() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN1, FEN2, 1, true);
        assertThat(entry.getEvalFormatted()).isEqualTo("?");
    }

    @Test
    @DisplayName("getEvalFormatted retorna valor positivo com sinal '+' após análise")
    void evalFormatted_positiveEval_returnsPlusSign() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN1, FEN2, 1, true);
        entry.setAnalysis(0.28, null, "d7d5", List.of("d7d5", "e7e5"));
        assertThat(entry.getEvalFormatted()).isEqualTo("+0.28");
    }

    @Test
    @DisplayName("getEvalFormatted retorna valor negativo com sinal '-' após análise")
    void evalFormatted_negativeEval_returnsMinusSign() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN1, FEN2, 1, true);
        entry.setAnalysis(-1.35, null, "d7d5", List.of("d7d5"));
        assertThat(entry.getEvalFormatted()).isEqualTo("-1.35");
    }

    @Test
    @DisplayName("getEvalFormatted retorna '+M3' para mate positivo em 3")
    void evalFormatted_mateInPositive_returnsPlusMate() {
        MoveEntry entry = new MoveEntry("d1h5", "Qh5", FEN1, FEN2, 1, true);
        entry.setAnalysis(null, 3, "d1h5", List.of());
        assertThat(entry.getEvalFormatted()).isEqualTo("+M3");
    }

    @Test
    @DisplayName("getEvalFormatted retorna '-M2' para mate negativo em 2")
    void evalFormatted_mateInNegative_returnsNegativeMate() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN1, FEN2, 1, true);
        entry.setAnalysis(null, -2, "d1h5", List.of());
        assertThat(entry.getEvalFormatted()).isEqualTo("-M2");
    }

    @Test
    @DisplayName("isAnalyzed passa para true após setAnalysis")
    void setAnalysis_setsAnalyzedTrue() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN1, FEN2, 1, true);
        assertThat(entry.isAnalyzed()).isFalse();
        entry.setAnalysis(0.0, null, "d7d5", List.of());
        assertThat(entry.isAnalyzed()).isTrue();
    }

    @Test
    @DisplayName("Getters retornam valores passados no construtor")
    void constructor_gettersReturnCorrectValues() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN1, FEN2, 1, true);
        assertThat(entry.getUci()).isEqualTo("e2e4");
        assertThat(entry.getSan()).isEqualTo("e4");
        assertThat(entry.getFenBefore()).isEqualTo(FEN1);
        assertThat(entry.getFenAfter()).isEqualTo(FEN2);
        assertThat(entry.getMoveNumber()).isEqualTo(1);
        assertThat(entry.isWhiteTurn()).isTrue();
    }

    @Test
    @DisplayName("getBestMove e getPv retornam valores definidos em setAnalysis")
    void setAnalysis_bestMoveAndPv_areStored() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN1, FEN2, 1, true);
        List<String> pv = List.of("d7d5", "e4d5");
        entry.setAnalysis(0.15, null, "d7d5", pv);
        assertThat(entry.getBestMove()).isEqualTo("d7d5");
        assertThat(entry.getPv()).isEqualTo(pv);
    }

    @Test
    @DisplayName("getEvalFormatted retorna '?' quando eval e mateIn são null mesmo após analyzed=true")
    void evalFormatted_bothNull_returnsQuestionMark() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN1, FEN2, 1, true);
        entry.setAnalysis(null, null, null, List.of());
        assertThat(entry.getEvalFormatted()).isEqualTo("?");
    }
}
