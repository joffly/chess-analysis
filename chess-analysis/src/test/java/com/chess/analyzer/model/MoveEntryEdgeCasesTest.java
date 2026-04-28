package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoveEntry — testes de casos extremos")
class MoveEntryEdgeCasesTest {

    private static final String FEN_START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String FEN_AFTER = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Test
    @DisplayName("getFenBefore e getFenAfter retornam valores distintos")
    void fen_values_distinct() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        
        assertThat(entry.getFenBefore()).isNotEqualTo(entry.getFenAfter());
    }

    @Test
    @DisplayName("setAnalysis com eval = 0 e mateIn = null")
    void setAnalysis_zeroEval() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        entry.setAnalysis(0.0, null, "d7d5", List.of());
        
        assertThat(entry.getEval()).isZero();
        assertThat(entry.getMateIn()).isNull();
    }

    @Test
    @DisplayName("setEvalAfter define valores independentes")
    void setEvalAfter_independent() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        
        entry.setAnalysis(0.5, null, "d7d5", List.of());
        entry.setEvalAfter(0.2, null);
        
        assertThat(entry.getEval()).isEqualTo(0.5);
        assertThat(entry.getEvalAfter()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("getPv retorna lista imutável")
    void getPv_immutable() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        List<String> pv = List.of("d7d5", "e4d5");
        
        entry.setAnalysis(0.3, null, "d7d5", pv);
        
        assertThat(entry.getPv()).isUnmodifiable();
    }

    @Test
    @DisplayName("getMoveNumber pode ser 0")
    void getMoveNumber_canBeZero() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 0, true);
        
        assertThat(entry.getMoveNumber()).isZero();
    }

    @Test
    @DisplayName("getMoveNumber pode ser grande")
    void getMoveNumber_canBeLarge() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1000, true);
        
        assertThat(entry.getMoveNumber()).isEqualTo(1000);
    }

    @Test
    @DisplayName("getEvalFormatted com eval 0.005 (arredonda para 0.01)")
    void getEvalFormatted_rounding() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        entry.setAnalysis(0.005, null, "d7d5", List.of());
        
        String formatted = entry.getEvalFormatted();
        assertThat(formatted).startsWith("+0.");
    }

    @Test
    @DisplayName("setAnalysis com null para todos os valores opcionais")
    void setAnalysis_allOptionalNull() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        entry.setAnalysis(null, null, null, List.of());
        
        assertThat(entry.getEval()).isNull();
        assertThat(entry.getMateIn()).isNull();
        assertThat(entry.getBestMove()).isNull();
    }

    @Test
    @DisplayName("isAnalyzed false quando setAnalysis não foi chamado")
    void isAnalyzed_initiallyFalse() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        
        assertThat(entry.isAnalyzed()).isFalse();
    }

    @Test
    @DisplayName("getEvalFormatted com eval = -0.0")
    void getEvalFormatted_negativeZero() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        entry.setAnalysis(-0.0, null, "d7d5", List.of());
        
        String formatted = entry.getEvalFormatted();
        assertThat(formatted).contains("0.");
    }

    @Test
    @DisplayName("getSan pode conter caracteres especiais")
    void getSan_specialCharacters() {
        MoveEntry entry = new MoveEntry("e1g1", "O-O", FEN_START, FEN_AFTER, 1, true);
        
        assertThat(entry.getSan()).isEqualTo("O-O");
    }

    @Test
    @DisplayName("getUci sempre em minúsculas")
    void getUci_lowercase() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        
        assertThat(entry.getUci()).isEqualTo("e2e4");
        assertThat(entry.getUci()).isLowerCase();
    }

    @Test
    @DisplayName("setEvalAfter pode ser chamado sem setAnalysis")
    void setEvalAfter_withoutSetAnalysis() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        
        entry.setEvalAfter(0.1, null);
        
        assertThat(entry.getEvalAfter()).isEqualTo(0.1);
        assertThat(entry.isAnalyzed()).isFalse();
    }

    @Test
    @DisplayName("getMateInAfter é null antes de setEvalAfter")
    void getMateInAfter_initiallyNull() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        
        assertThat(entry.getMateInAfter()).isNull();
    }

    @Test
    @DisplayName("setAnalysis com PV contendo promoção")
    void setAnalysis_pvWithPromotion() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        List<String> pv = List.of("d7d5", "e4d5", "e7e8q");
        
        entry.setAnalysis(0.5, null, "d7d5", pv);
        
        assertThat(entry.getPv()).hasSize(3);
        assertThat(entry.getPv().get(2)).isEqualTo("e7e8q");
    }

    @Test
    @DisplayName("getBestMove retorna valor exato do setAnalysis")
    void getBestMove_exactValue() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        entry.setAnalysis(0.5, null, "a2a3", List.of());
        
        assertThat(entry.getBestMove()).isEqualTo("a2a3");
    }

    @Test
    @DisplayName("isWhiteTurn true para lance das brancas")
    void isWhiteTurn_trueForWhite() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER, 1, true);
        
        assertThat(entry.isWhiteTurn()).isTrue();
    }

    @Test
    @DisplayName("isWhiteTurn false para lance das pretas")
    void isWhiteTurn_falseForBlack() {
        MoveEntry entry = new MoveEntry("e7e5", "e5", FEN_AFTER, FEN_START, 1, false);
        
        assertThat(entry.isWhiteTurn()).isFalse();
    }
}
