package com.chess.analyzer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LanceEntity — testes de serialização")
class LanceEntitySerializationTest {

    private static final String FEN1 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String FEN2 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Test
    @DisplayName("getVariantePrincipal retorna null antes de registrarAnalise")
    void getVariantePrincipal_nullBeforeAnalysis() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.getVariantePrincipal()).isNull();
    }

    @Test
    @DisplayName("getVariantePrincipal com PV serializado com espaços")
    void getVariantePrincipal_serializedWithSpaces() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        List<String> pv = List.of("d7d5", "e4d5", "d8d5", "b1c3");
        
        lance.registrarAnalise(0.5, null, "d7d5", pv, false);
        
        assertThat(lance.getVariantePrincipal()).isEqualTo("d7d5 e4d5 d8d5 b1c3");
    }

    @Test
    @DisplayName("getVariantePrincipal com PV vazio resulta em vazio")
    void getVariantePrincipal_emptyPv() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, "d7d5", List.of(), false);
        
        assertThat(lance.getVariantePrincipal()).isEmpty();
    }

    @Test
    @DisplayName("getVariantePrincipal com PV com um lance")
    void getVariantePrincipal_singleMove() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, "d7d5", List.of("d7d5"), false);
        
        assertThat(lance.getVariantePrincipal()).isEqualTo("d7d5");
    }

    @Test
    @DisplayName("getVariantePrincipal com null PV resulta em null")
    void getVariantePrincipal_nullPv() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, "d7d5", null, false);
        
        assertThat(lance.getVariantePrincipal()).isNull();
    }

    @Test
    @DisplayName("getMelhorLance retorna null antes de registrarAnalise")
    void getMelhorLance_nullBeforeAnalysis() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.getMelhorLance()).isNull();
    }

    @Test
    @DisplayName("getMelhorLance com valor setado")
    void getMelhorLance_withValue() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, "d7d5", List.of(), false);
        
        assertThat(lance.getMelhorLance()).isEqualTo("d7d5");
    }

    @Test
    @DisplayName("getMelhorLance com null")
    void getMelhorLance_null() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, null, List.of(), false);
        
        assertThat(lance.getMelhorLance()).isNull();
    }

    @Test
    @DisplayName("getEval retorna null antes de registrarAnalise")
    void getEval_nullBeforeAnalysis() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.getEval()).isNull();
    }

    @Test
    @DisplayName("getEval com valor positivo")
    void getEval_positive() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, "d7d5", List.of(), false);
        
        assertThat(lance.getEval()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("getEval com valor negativo")
    void getEval_negative() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(-2.5, null, "d7d5", List.of(), false);
        
        assertThat(lance.getEval()).isEqualTo(-2.5);
    }

    @Test
    @DisplayName("getMateEm retorna null antes de registrarAnalise")
    void getMateEm_nullBeforeAnalysis() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.getMateEm()).isNull();
    }

    @Test
    @DisplayName("getMateEm com valor positivo")
    void getMateEm_positive() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(null, 5, "d7d5", List.of(), false);
        
        assertThat(lance.getMateEm()).isEqualTo(5);
    }

    @Test
    @DisplayName("getMateEm com valor negativo")
    void getMateEm_negative() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(null, -3, "d7d5", List.of(), false);
        
        assertThat(lance.getMateEm()).isEqualTo(-3);
    }

    @Test
    @DisplayName("registrarAnalise sobrescreve valores anteriores")
    void registrarAnalise_overwrite() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, "d7d5", List.of("d7d5"), false);
        assertThat(lance.getEval()).isEqualTo(0.5);
        
        lance.registrarAnalise(0.3, null, "e7e5", List.of("e7e5"), false);
        assertThat(lance.getEval()).isEqualTo(0.3);
        assertThat(lance.getMelhorLance()).isEqualTo("e7e5");
    }

    @Test
    @DisplayName("registrarAnalise com avaliação 0")
    void registrarAnalise_zeroEval() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.0, null, "d7d5", List.of(), false);
        
        assertThat(lance.getEval()).isZero();
    }

    @Test
    @DisplayName("registrarAnalise com blunder true")
    void registrarAnalise_blunderTrue() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(-2.0, null, "d7d5", List.of(), true);
        
        assertThat(lance.isBlunder()).isTrue();
    }

    @Test
    @DisplayName("registrarAnalise com blunder false")
    void registrarAnalise_blunderFalse() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.3, null, "d7d5", List.of(), false);
        
        assertThat(lance.isBlunder()).isFalse();
    }

    @Test
    @DisplayName("isAnalisado false antes de registrarAnalise")
    void isAnalisado_falseBeforeAnalysis() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.isAnalisado()).isFalse();
    }

    @Test
    @DisplayName("isAnalisado true após registrarAnalise")
    void isAnalisado_trueAfterAnalysis() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        lance.registrarAnalise(0.5, null, "d7d5", List.of(), false);
        
        assertThat(lance.isAnalisado()).isTrue();
    }

    @Test
    @DisplayName("isBlunder false por padrão")
    void isBlunder_falseByDefault() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        
        assertThat(lance.isBlunder()).isFalse();
    }

    @Test
    @DisplayName("evalFormatado com análise completa")
    void evalFormatado_fullyAnalyzed() {
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", FEN1, FEN2);
        lance.registrarAnalise(0.85, null, "d7d5", List.of(), false);
        
        assertThat(lance.evalFormatado()).isEqualTo("+0.85");
    }
}
