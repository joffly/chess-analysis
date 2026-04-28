package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoveEntry — testes de promoção")
class MoveEntryPromotionTest {

    private static final String FEN_BEFORE_PROMO = "8/P7/8/8/8/8/8/8 w - - 0 1";
    private static final String FEN_AFTER_PROMO = "Q7/8/8/8/8/8/8/8 b - - 0 1";

    @Test
    @DisplayName("Lance com promoção rainha")
    void promotion_queen() {
        MoveEntry entry = new MoveEntry("a7a8q", "a8=Q", FEN_BEFORE_PROMO, FEN_AFTER_PROMO, 1, true);
        
        assertThat(entry.getUci()).isEqualTo("a7a8q");
        assertThat(entry.getSan()).isEqualTo("a8=Q");
    }

    @Test
    @DisplayName("Lance com promoção torre")
    void promotion_rook() {
        MoveEntry entry = new MoveEntry("a7a8r", "a8=R", FEN_BEFORE_PROMO, FEN_AFTER_PROMO, 1, true);
        
        assertThat(entry.getUci()).isEqualTo("a7a8r");
        assertThat(entry.getSan()).isEqualTo("a8=R");
    }

    @Test
    @DisplayName("Lance com promoção bispo")
    void promotion_bishop() {
        MoveEntry entry = new MoveEntry("a7a8b", "a8=B", FEN_BEFORE_PROMO, FEN_AFTER_PROMO, 1, true);
        
        assertThat(entry.getUci()).isEqualTo("a7a8b");
        assertThat(entry.getSan()).isEqualTo("a8=B");
    }

    @Test
    @DisplayName("Lance com promoção cavalo")
    void promotion_knight() {
        MoveEntry entry = new MoveEntry("a7a8n", "a8=N", FEN_BEFORE_PROMO, FEN_AFTER_PROMO, 1, true);
        
        assertThat(entry.getUci()).isEqualTo("a7a8n");
        assertThat(entry.getSan()).isEqualTo("a8=N");
    }

    @Test
    @DisplayName("Lance sem promoção na classificação")
    void noPromotion() {
        MoveEntry entry = new MoveEntry("e2e4", "e4", FEN_BEFORE_PROMO, FEN_AFTER_PROMO, 1, true);
        
        assertThat(entry.getUci()).doesNotContain("q", "r", "b", "n");
    }

    @Test
    @DisplayName("setAnalysis com promoção detectada no SAN")
    void setAnalysis_withPromotionSan() {
        MoveEntry entry = new MoveEntry("a7a8q", "a8=Q", FEN_BEFORE_PROMO, FEN_AFTER_PROMO, 1, true);
        entry.setAnalysis(5.0, null, "a7a8q", List.of("a7a8q"));
        
        assertThat(entry.getSan()).contains("=");
        assertThat(entry.isAnalyzed()).isTrue();
    }

    @Test
    @DisplayName("getEvalFormatted após análise de promoção")
    void getEvalFormatted_promotion() {
        MoveEntry entry = new MoveEntry("a7a8q", "a8=Q", FEN_BEFORE_PROMO, FEN_AFTER_PROMO, 1, true);
        entry.setAnalysis(8.0, null, "a7a8q", List.of());
        
        assertThat(entry.getEvalFormatted()).isEqualTo("+8.00");
    }

    @Test
    @DisplayName("Múltiplas promoções em variante principal")
    void multiplePromotionsInPv() {
        MoveEntry entry = new MoveEntry("a7a8q", "a8=Q", FEN_BEFORE_PROMO, FEN_AFTER_PROMO, 1, true);
        List<String> pv = List.of("a7a8q", "h7h8q", "a8a1");
        
        entry.setAnalysis(10.0, null, "a7a8q", pv);
        
        assertThat(entry.getPv()).hasSize(3);
        assertThat(entry.getPv().get(1)).isEqualTo("h7h8q");
    }
}
