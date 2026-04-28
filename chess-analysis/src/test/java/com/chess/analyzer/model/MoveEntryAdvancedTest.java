package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoveEntry — testes avançados")
class MoveEntryAdvancedTest {

    private static final String FEN_START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String FEN_AFTER_E4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Test
    @DisplayName("setEvalAfter define evalAfter e mateInAfter corretamente")
    void setEvalAfter_setsValuesCorrectly() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        
        entry.setEvalAfter(0.15, null);
        
        assertThat(entry.getEvalAfter()).isEqualTo(0.15);
        assertThat(entry.getMateInAfter()).isNull();
    }

    @Test
    @DisplayName("setEvalAfter com mate after")
    void setEvalAfter_withMateAfter() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        
        entry.setEvalAfter(null, 5);
        
        assertThat(entry.getEvalAfter()).isNull();
        assertThat(entry.getMateInAfter()).isEqualTo(5);
    }

    @Test
    @DisplayName("setAnalysis e setEvalAfter podem ser chamados sequencialmente")
    void setAnalysis_andSetEvalAfter() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        
        entry.setAnalysis(0.28, null, "d7d5", List.of("d7d5"));
        entry.setEvalAfter(0.15, null);
        
        assertThat(entry.isAnalyzed()).isTrue();
        assertThat(entry.getEval()).isEqualTo(0.28);
        assertThat(entry.getEvalAfter()).isEqualTo(0.15);
    }

    @Test
    @DisplayName("isWhiteTurn retorna valor correto para brancas")
    void isWhiteTurn_whiteTrue() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        assertThat(entry.isWhiteTurn()).isTrue();
    }

    @Test
    @DisplayName("isWhiteTurn retorna valor correto para pretas")
    void isWhiteTurn_blackFalse() {
        MoveEntry entry = new MoveEntry("e7e5", "e5", FEN_AFTER_E4, FEN_START, 1, false);
        assertThat(entry.isWhiteTurn()).isFalse();
    }

    @Test
    @DisplayName("setAnalysis com null para bestMove")
    void setAnalysis_nullBestMove() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        entry.setAnalysis(0.5, null, null, List.of());
        
        assertThat(entry.getBestMove()).isNull();
    }

    @Test
    @DisplayName("setAnalysis com PV vazio")
    void setAnalysis_emptyPv() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        entry.setAnalysis(0.5, null, "d7d5", List.of());
        
        assertThat(entry.getPv()).isEmpty();
    }

    @Test
    @DisplayName("setAnalysis com PV com múltiplos lances")
    void setAnalysis_multiplePvMoves() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        List<String> pv = List.of("d7d5", "e4d5", "d8d5", "b1c3", "d5d8");
        
        entry.setAnalysis(0.3, null, "d7d5", pv);
        
        assertThat(entry.getPv()).hasSize(5);
        assertThat(entry.getPv()).containsExactly("d7d5", "e4d5", "d5d8", "b1c3", "d5d8");
    }

    @Test
    @DisplayName("getEvalFormatted com 0 avaliação")
    void getEvalFormatted_zeroEval() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        entry.setAnalysis(0.0, null, "d7d5", List.of());
        
        assertThat(entry.getEvalFormatted()).isEqualTo("+0.00");
    }

    @Test
    @DisplayName("getEvalFormatted com -0.0 avaliação")
    void getEvalFormatted_negativeZeroEval() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        entry.setAnalysis(-0.0, null, "d7d5", List.of());
        
        assertThat(entry.getEvalFormatted()).contains("0.00");
    }

    @Test
    @DisplayName("getEvalFormatted com mate -1 (mate na próxima)")
    void getEvalFormatted_mateInOne() {
        MoveEntry entry = new MoveEntry("d1h5", "Qh5", FEN_START, FEN_AFTER_E4, 1, true);
        entry.setAnalysis(null, -1, "e8e7", List.of());
        
        assertThat(entry.getEvalFormatted()).isEqualTo("-M1");
    }

    @Test
    @DisplayName("getEvalFormatted com mate +20")
    void getEvalFormatted_mateInMany() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        entry.setAnalysis(null, 20, "d7d5", List.of());
        
        assertThat(entry.getEvalFormatted()).isEqualTo("+M20");
    }

    @Test
    @DisplayName("getMoveNumber retorna valor do construtor")
    void getMoveNumber_returnsConstructorValue() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 15, true);
        assertThat(entry.getMoveNumber()).isEqualTo(15);
    }

    @Test
    @DisplayName("getUci e getSan retornam valores do construtor")
    void getUci_getSan_returnsConstructorValues() {
        MoveEntry entry = new MoveEntry("g1f3", "Nf3", FEN_START, FEN_AFTER_E4, 1, true);
        assertThat(entry.getUci()).isEqualTo("g1f3");
        assertThat(entry.getSan()).isEqualTo("Nf3");
    }

    @Test
    @DisplayName("Ordem de chamadas: setAnalysis então getters")
    void setAnalysis_thenGetters() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        List<String> pv = List.of("d7d5");
        
        entry.setAnalysis(0.75, 2, "d7d5", pv);
        
        assertThat(entry.getEval()).isEqualTo(0.75);
        assertThat(entry.getMateIn()).isEqualTo(2);
        assertThat(entry.getBestMove()).isEqualTo("d7d5");
        assertThat(entry.getPv()).containsExactly("d7d5");
    }

    @Test
    @DisplayName("MoveEntry é inicializado com analyzed=false e getters iniciais")
    void initialization_defaults() {
        MoveEntry entry = new MoveEntry("a2a3", "a3", FEN_START, FEN_AFTER_E4, 1, true);
        
        assertThat(entry.isAnalyzed()).isFalse();
        assertThat(entry.getEval()).isNull();
        assertThat(entry.getMateIn()).isNull();
        assertThat(entry.getBestMove()).isNull();
        assertThat(entry.getPv()).isNull();
    }

    @Test
    @DisplayName("getFenBefore e getFenAfter retornam FEN correto")
    void getFenBefore_getFenAfter() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        
        assertThat(entry.getFenBefore()).isEqualTo(FEN_START);
        assertThat(entry.getFenAfter()).isEqualTo(FEN_AFTER_E4);
    }

    @Test
    @DisplayName("setAnalysis com eval negativo muito pequeno")
    void setAnalysis_verySmallNegativeEval() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        entry.setAnalysis(-0.01, null, "d7d5", List.of());
        
        assertThat(entry.getEvalFormatted()).isEqualTo("-0.01");
    }

    @Test
    @DisplayName("setAnalysis com eval positivo muito pequeno")
    void setAnalysis_verySmallPositiveEval() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_START, FEN_AFTER_E4, 1, true);
        entry.setAnalysis(0.01, null, "d7d5", List.of());
        
        assertThat(entry.getEvalFormatted()).isEqualTo("+0.01");
    }
}
