package com.chess.analyzer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LanceEntity — testes unitários")
class LanceEntityTest {

    private static final String FEN1 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String FEN2 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Test
    @DisplayName("construtor inicializa campos corretamente")
    void constructor_initializesFieldsCorrectly() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.getOrdem()).isEqualTo(1);
        assertThat(lance.getNumeroLance()).isEqualTo(1);
        assertThat(lance.isVezBrancas()).isTrue();
        assertThat(lance.getUci()).isEqualTo("e2e4");
        assertThat(lance.getSan()).isEqualTo("e4");
        assertThat(lance.getFenAntes()).isEqualTo(FEN1);
        assertThat(lance.getFenDepois()).isEqualTo(FEN2);
    }

    @Test
    @DisplayName("construtor marca analisado=false por padrão")
    void constructor_analyzedFalseByDefault() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.isAnalisado()).isFalse();
        assertThat(lance.isBlunder()).isFalse();
    }

    @Test
    @DisplayName("registrarAnalise preenche campos de análise")
    void registrarAnalise_fillsAnalysisFields() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.42, null, "d7d5", List.of("d7d5", "e7e5"), false);
        
        assertThat(lance.getEval()).isEqualTo(0.42);
        assertThat(lance.getMateEm()).isNull();
        assertThat(lance.getMelhorLance()).isEqualTo("d7d5");
        assertThat(lance.isAnalisado()).isTrue();
        assertThat(lance.isBlunder()).isFalse();
    }

    @Test
    @DisplayName("registrarAnalise com mate positivo")
    void registrarAnalise_mateIn() {
        LanceEntity lance = new LanceEntity(1, 1, true, "d1h5", "Qh5", FEN1, FEN2);
        
        lance.registrarAnalise(null, 3, "d1h5", List.of("d1h5"), true);
        
        assertThat(lance.getMateEm()).isEqualTo(3);
        assertThat(lance.isBlunder()).isTrue();
    }

    @Test
    @DisplayName("registrarAnalise com blunder=true")
    void registrarAnalise_blunderTrue() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(-2.0, null, "d7d5", List.of(), true);
        
        assertThat(lance.isBlunder()).isTrue();
    }

    @Test
    @DisplayName("registrarAnalise serializa PV corretamente")
    void registrarAnalise_serializesPv() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        List<String> pv = List.of("d7d5", "e4d5", "d8d5");
        
        lance.registrarAnalise(0.5, null, "d7d5", pv, false);
        
        assertThat(lance.getVariantePrincipal()).isEqualTo("d7d5 e4d5 d8d5");
    }

    @Test
    @DisplayName("evalFormatado retorna '?' antes da análise")
    void evalFormatado_notAnalyzed_returnsQuestionMark() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.evalFormatado()).isEqualTo("?");
    }

    @Test
    @DisplayName("evalFormatado retorna '+0.42' para eval positivo")
    void evalFormatado_positiveEval() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(0.42, null, "d7d5", List.of(), false);
        
        assertThat(lance.evalFormatado()).isEqualTo("+0.42");
    }

    @Test
    @DisplayName("evalFormatado retorna '-1.35' para eval negativo")
    void evalFormatado_negativeEval() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(-1.35, null, "d7d5", List.of(), false);
        
        assertThat(lance.evalFormatado()).isEqualTo("-1.35");
    }

    @Test
    @DisplayName("evalFormatado retorna '+M3' para mate positivo")
    void evalFormatado_matePositive() {
        LanceEntity lance = new LanceEntity(1, 1, true, "d1h5", "Qh5", FEN1, FEN2);
        lance.registrarAnalise(null, 3, "d1h5", List.of(), false);
        
        assertThat(lance.evalFormatado()).isEqualTo("+M3");
    }

    @Test
    @DisplayName("evalFormatado retorna '-M2' para mate negativo")
    void evalFormatado_mateNegative() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(null, -2, "d1h5", List.of(), false);
        
        assertThat(lance.evalFormatado()).isEqualTo("-M2");
    }

    @Test
    @DisplayName("evalFormatado retorna '?' quando eval e mate são null mas analisado=true")
    void evalFormatado_bothNullAnalyzed_returnsQuestionMark() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(null, null, null, List.of(), false);
        
        assertThat(lance.evalFormatado()).isEqualTo("?");
    }

    @Test
    @DisplayName("véz das pretas é representada corretamente")
    void constructor_blackTurnCorrectly() {
        LanceEntity lance = new LanceEntity(2, 1, false, "e7e5", "e5", FEN2, FEN1);
        
        assertThat(lance.isVezBrancas()).isFalse();
    }

    @Test
    @DisplayName("ID é null antes de salvar no banco")
    void getId_nullBeforePersist() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.getId()).isNull();
    }

    @Test
    @DisplayName("associarPartida vincula a partida")
    void associarPartida_linksPartida() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", java.util.Map.of(), FEN1);
        
        lance.associarPartida(partida);
        
        assertThat(lance.getPartida()).isEqualTo(partida);
    }

    @Test
    @DisplayName("registrarAnalise com PV nula serializa como null")
    void registrarAnalise_nullPv() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, "d7d5", null, false);
        
        assertThat(lance.getVariantePrincipal()).isNull();
    }

    @Test
    @DisplayName("construtor com diferentes números de lance")
    void constructor_differentMoveNumbers() {
        LanceEntity l1 = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        LanceEntity l2 = new LanceEntity(2, 1, false, "e7e5", "e5", FEN2, FEN1);
        
        assertThat(l1.getNumeroLance()).isEqualTo(l2.getNumeroLance());
        assertThat(l1.getOrdem()).isNotEqualTo(l2.getOrdem());
    }

    @Test
    @DisplayName("evalFormatado formata com 2 casas decimais")
    void evalFormatado_twoDecimalPlaces() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(0.1, null, "d7d5", List.of(), false);
        
        String formatted = lance.evalFormatado();
        assertThat(formatted).matches("\\+0\\.10");
    }
}
