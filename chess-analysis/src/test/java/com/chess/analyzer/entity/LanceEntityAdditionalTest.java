package com.chess.analyzer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LanceEntity — testes adicionais")
class LanceEntityAdditionalTest {

    private static final String FEN1 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String FEN2 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Test
    @DisplayName("construtor com ordem 0 (válido)")
    void constructor_orderZero() {
        LanceEntity lance = new LanceEntity(0, 1, true, "e2e4", "e4", FEN1, FEN2);
        assertThat(lance.getOrdem()).isEqualTo(0);
    }

    @Test
    @DisplayName("construtor com ordem grande")
    void constructor_largeOrder() {
        LanceEntity lance = new LanceEntity(1000, 500, true, "e2e4", "e4", FEN1, FEN2);
        assertThat(lance.getOrdem()).isEqualTo(1000);
    }

    @Test
    @DisplayName("registrarAnalise com eval 0.0")
    void registrarAnalise_zeroEval() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(0.0, null, "d7d5", List.of(), false);
        
        assertThat(lance.getEval()).isEqualTo(0.0);
        assertThat(lance.evalFormatado()).isEqualTo("+0.00");
    }

    @Test
    @DisplayName("registrarAnalise com eval negativo muito grande")
    void registrarAnalise_veryNegativeEval() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(-50.0, null, "d7d5", List.of(), false);
        
        assertThat(lance.getEval()).isEqualTo(-50.0);
        assertThat(lance.evalFormatado()).isEqualTo("-50.00");
    }

    @Test
    @DisplayName("registrarAnalise com mateIn negativo grande")
    void registrarAnalise_veryNegativeMate() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(null, -20, "d7d5", List.of(), true);
        
        assertThat(lance.getMateEm()).isEqualTo(-20);
        assertThat(lance.evalFormatado()).isEqualTo("-M20");
    }

    @Test
    @DisplayName("registrarAnalise com PV com espaços é serializado")
    void registrarAnalise_pvWithMultipleMoves() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        List<String> pv = List.of("d7d5", "e4d5", "d8d5", "b1c3");
        
        lance.registrarAnalise(0.5, null, "d7d5", pv, false);
        
        assertThat(lance.getVariantePrincipal()).isEqualTo("d7d5 e4d5 d8d5 b1c3");
    }

    @Test
    @DisplayName("evalFormatado com eval 9.99")
    void evalFormatado_largePositive() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(9.99, null, "d7d5", List.of(), false);
        
        assertThat(lance.evalFormatado()).isEqualTo("+9.99");
    }

    @Test
    @DisplayName("evalFormatado com eval -9.99")
    void evalFormatado_largeNegative() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(-9.99, null, "d7d5", List.of(), false);
        
        assertThat(lance.evalFormatado()).isEqualTo("-9.99");
    }

    @Test
    @DisplayName("registrarAnalise com blunder=false")
    void registrarAnalise_blunderFalse() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(0.1, null, "d7d5", List.of(), false);
        
        assertThat(lance.isBlunder()).isFalse();
    }

    @Test
    @DisplayName("registrarAnalise marca analisado=true")
    void registrarAnalise_marksAnalyzed() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        assertThat(lance.isAnalisado()).isFalse();
        
        lance.registrarAnalise(0.0, null, null, List.of(), false);
        assertThat(lance.isAnalisado()).isTrue();
    }

    @Test
    @DisplayName("registrarAnalise pode ser chamado múltiplas vezes (sobrescreve)")
    void registrarAnalise_canBeCalledMultipleTimes() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, "d7d5", List.of("d7d5"), false);
        lance.registrarAnalise(0.3, null, "e7e5", List.of("e7e5"), false);
        
        assertThat(lance.getEval()).isEqualTo(0.3);
        assertThat(lance.getMelhorLance()).isEqualTo("e7e5");
    }

    @Test
    @DisplayName("getSan com notação de roque curto")
    void constructor_castlingKingSide() {
        LanceEntity lance = new LanceEntity(1, 10, true, "e1g1", "O-O", FEN1, FEN2);
        assertThat(lance.getSan()).isEqualTo("O-O");
    }

    @Test
    @DisplayName("getSan com notação de roque longo")
    void constructor_castlingQueenSide() {
        LanceEntity lance = new LanceEntity(1, 10, true, "e1c1", "O-O-O", FEN1, FEN2);
        assertThat(lance.getSan()).isEqualTo("O-O-O");
    }

    @Test
    @DisplayName("getUci com promoção de rainha")
    void constructor_promotionQueenUci() {
        LanceEntity lance = new LanceEntity(1, 40, false, "e7e8q", "e8=Q", FEN1, FEN2);
        assertThat(lance.getUci()).isEqualTo("e7e8q");
        assertThat(lance.getSan()).isEqualTo("e8=Q");
    }

    @Test
    @DisplayName("getNumeroLance diferente de getOrdem")
    void constructor_moveNumberVsOrder() {
        LanceEntity l1 = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        LanceEntity l2 = new LanceEntity(2, 1, false, "e7e5", "e5", FEN2, FEN1);
        
        assertThat(l1.getNumeroLance()).isEqualTo(l2.getNumeroLance());
        assertThat(l1.getOrdem()).isNotEqualTo(l2.getOrdem());
    }

    @Test
    @DisplayName("registrarAnalise com eval extremo positivo")
    void registrarAnalise_extremePositive() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(999.99, null, "d7d5", List.of(), false);
        
        assertThat(lance.evalFormatado()).contains("999");
    }

    @Test
    @DisplayName("registrarAnalise com eval extremo negativo")
    void registrarAnalise_extremeNegative() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(-999.99, null, "d7d5", List.of(), false);
        
        assertThat(lance.evalFormatado()).contains("999");
    }

    @Test
    @DisplayName("registrarAnalise com PV com um só lance")
    void registrarAnalise_singleMovePv() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        List<String> pv = List.of("d7d5");
        
        lance.registrarAnalise(0.3, null, "d7d5", pv, false);
        
        assertThat(lance.getVariantePrincipal()).isEqualTo("d7d5");
    }

    @Test
    @DisplayName("registrarAnalise com PV vazio resulta em vazio serializado")
    void registrarAnalise_emptyPvResultsInEmptyString() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.3, null, "d7d5", List.of(), false);
        
        assertThat(lance.getVariantePrincipal()).isEmpty();
    }
}
