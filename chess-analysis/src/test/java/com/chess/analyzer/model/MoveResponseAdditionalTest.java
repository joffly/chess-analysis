package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoveResponse — testes adicionais")
class MoveResponseAdditionalTest {

    @Test
    @DisplayName("error() com mensagem vazia")
    void error_emptyMessage() {
        MoveResponse resp = MoveResponse.error("");
        assertThat(resp.ok()).isFalse();
        assertThat(resp.message()).isEmpty();
    }

    @Test
    @DisplayName("record com todos os booleanos true")
    void record_allBooleansTrue() {
        MoveResponse resp = new MoveResponse(true, "fen", "Qf7#", "d1f7", true, true, false, true, null);
        assertThat(resp.ok()).isTrue();
        assertThat(resp.check()).isTrue();
        assertThat(resp.checkmate()).isTrue();
        assertThat(resp.gameOver()).isTrue();
        assertThat(resp.stalemate()).isFalse();
    }

    @Test
    @DisplayName("record com todos os booleanos false")
    void record_allBooleansFalse() {
        MoveResponse resp = new MoveResponse(false, null, null, null, false, false, false, false, "erro");
        assertThat(resp.ok()).isFalse();
        assertThat(resp.check()).isFalse();
        assertThat(resp.checkmate()).isFalse();
        assertThat(resp.stalemate()).isFalse();
        assertThat(resp.gameOver()).isFalse();
    }

    @Test
    @DisplayName("record com stalemate=true")
    void record_stalemateTrue() {
        MoveResponse resp = new MoveResponse(true, "fen", "?", "uci", false, false, true, true, null);
        assertThat(resp.stalemate()).isTrue();
        assertThat(resp.gameOver()).isTrue();
        assertThat(resp.checkmate()).isFalse();
    }

    @Test
    @DisplayName("error com mensagem longa")
    void error_longMessage() {
        String msg = "Este é um erro muito longo que descreve o problema em detalhes e tem muitos caracteres";
        MoveResponse resp = MoveResponse.error(msg);
        assertThat(resp.message()).isEqualTo(msg);
        assertThat(resp.ok()).isFalse();
    }

    @Test
    @DisplayName("record com SAN vazio")
    void record_emptySan() {
        MoveResponse resp = new MoveResponse(true, "fen", "", "e2e4", false, false, false, false, null);
        assertThat(resp.san()).isEmpty();
    }

    @Test
    @DisplayName("record com UCI vazio")
    void record_emptyUci() {
        MoveResponse resp = new MoveResponse(true, "fen", "e4", "", false, false, false, false, null);
        assertThat(resp.uci()).isEmpty();
    }

    @Test
    @DisplayName("record com FEN vazio")
    void record_emptyFen() {
        MoveResponse resp = new MoveResponse(true, "", "e4", "e2e4", false, false, false, false, null);
        assertThat(resp.fen()).isEmpty();
    }

    @Test
    @DisplayName("dois records iguais com mesmos valores")
    void record_equalityByValue() {
        MoveResponse r1 = new MoveResponse(true, "fen", "e4", "e2e4", false, false, false, false, null);
        MoveResponse r2 = new MoveResponse(true, "fen", "e4", "e2e4", false, false, false, false, null);
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    @DisplayName("dois records diferentes não são iguais")
    void record_inequality() {
        MoveResponse r1 = new MoveResponse(true, "fen1", "e4", "e2e4", false, false, false, false, null);
        MoveResponse r2 = new MoveResponse(true, "fen2", "e4", "e2e4", false, false, false, false, null);
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    @DisplayName("hashCode igual para records equivalentes")
    void record_hashCodeEquality() {
        MoveResponse r1 = new MoveResponse(true, "fen", "e4", "e2e4", false, false, false, false, null);
        MoveResponse r2 = new MoveResponse(true, "fen", "e4", "e2e4", false, false, false, false, null);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("error() retorna ok=false com fen=null")
    void error_fenIsNull() {
        MoveResponse resp = MoveResponse.error("Erro");
        assertThat(resp.fen()).isNull();
    }

    @Test
    @DisplayName("error() retorna ok=false com san=null")
    void error_sanIsNull() {
        MoveResponse resp = MoveResponse.error("Erro");
        assertThat(resp.san()).isNull();
    }

    @Test
    @DisplayName("error() retorna ok=false com uci=null")
    void error_uciIsNull() {
        MoveResponse resp = MoveResponse.error("Erro");
        assertThat(resp.uci()).isNull();
    }

    @Test
    @DisplayName("toString contém todos os valores")
    void record_toString() {
        MoveResponse resp = new MoveResponse(true, "fen", "e4", "e2e4", true, false, false, false, null);
        String str = resp.toString();
        assertThat(str).contains("e4").contains("e2e4").contains("true");
    }

    @Test
    @DisplayName("record com check=true e checkmate=false")
    void record_checkWithoutCheckmate() {
        MoveResponse resp = new MoveResponse(true, "fen", "Qf7+", "d1f7", true, false, false, false, null);
        assertThat(resp.check()).isTrue();
        assertThat(resp.checkmate()).isFalse();
    }

    @Test
    @DisplayName("record com checkmate=true implica check=true semanticamente")
    void record_checkmate_impliesCheck() {
        MoveResponse resp = new MoveResponse(true, "fen", "Qf7#", "d1f7", true, true, false, true, null);
        assertThat(resp.checkmate()).isTrue();
        assertThat(resp.check()).isTrue();
    }

    @Test
    @DisplayName("error com caracteres especiais na mensagem")
    void error_specialCharactersMessage() {
        String msg = "Erro: [a2a4] é inválido @ posição #1!";
        MoveResponse resp = MoveResponse.error(msg);
        assertThat(resp.message()).isEqualTo(msg);
    }
}
