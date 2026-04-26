package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoveResponse — testes unitários")
class MoveResponseTest {

    @Test
    @DisplayName("error() retorna ok=false com mensagem e todos os campos nulos/false")
    void error_setsAllFieldsCorrectly() {
        MoveResponse resp = MoveResponse.error("Lance ilegal: e2e5");

        assertThat(resp.ok()).isFalse();
        assertThat(resp.message()).isEqualTo("Lance ilegal: e2e5");
        assertThat(resp.fen()).isNull();
        assertThat(resp.san()).isNull();
        assertThat(resp.uci()).isNull();
        assertThat(resp.check()).isFalse();
        assertThat(resp.checkmate()).isFalse();
        assertThat(resp.stalemate()).isFalse();
        assertThat(resp.gameOver()).isFalse();
    }

    @Test
    @DisplayName("Construtor completo preenche todos os campos corretamente")
    void fullConstructor_allFields() {
        MoveResponse resp = new MoveResponse(
                true, "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                "e4", "e2e4", false, false, false, false, null);

        assertThat(resp.ok()).isTrue();
        assertThat(resp.fen()).isNotBlank();
        assertThat(resp.san()).isEqualTo("e4");
        assertThat(resp.uci()).isEqualTo("e2e4");
        assertThat(resp.check()).isFalse();
        assertThat(resp.checkmate()).isFalse();
        assertThat(resp.stalemate()).isFalse();
        assertThat(resp.gameOver()).isFalse();
        assertThat(resp.message()).isNull();
    }

    @Test
    @DisplayName("error() com mensagem nula não lança exceção")
    void error_withNullMessage_doesNotThrow() {
        MoveResponse resp = MoveResponse.error(null);
        assertThat(resp.ok()).isFalse();
        assertThat(resp.message()).isNull();
    }

    @Test
    @DisplayName("MoveResponse com check=true reflete no campo check")
    void fullConstructor_checkTrue() {
        MoveResponse resp = new MoveResponse(
                true, "some-fen", "Qf7+", "d1f7",
                true, false, false, false, null);
        assertThat(resp.check()).isTrue();
        assertThat(resp.checkmate()).isFalse();
    }

    @Test
    @DisplayName("MoveResponse com checkmate=true implica gameOver=true")
    void fullConstructor_checkmateAndGameOver() {
        MoveResponse resp = new MoveResponse(
                true, "some-fen", "Qf7#", "d1f7",
                true, true, false, true, null);
        assertThat(resp.checkmate()).isTrue();
        assertThat(resp.gameOver()).isTrue();
        assertThat(resp.san()).endsWith("#");
    }
}
