package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoveRequest — testes unitários")
class MoveRequestTest {

    @Test
    @DisplayName("record com todos os campos armazena valores corretamente")
    void record_allFieldsStored() {
        MoveRequest req = new MoveRequest("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "e2", "e4", null);
        assertThat(req.fen()).startsWith("rnbqkbnr");
        assertThat(req.from()).isEqualTo("e2");
        assertThat(req.to()).isEqualTo("e4");
        assertThat(req.promotion()).isNull();
    }

    @Test
    @DisplayName("record com promoção armazena corretamente")
    void record_withPromotion() {
        MoveRequest req = new MoveRequest("8/P7/8/8/8/8/8/8 w - - 0 1", "a7", "a8", "q");
        assertThat(req.from()).isEqualTo("a7");
        assertThat(req.to()).isEqualTo("a8");
        assertThat(req.promotion()).isEqualTo("q");
    }

    @Test
    @DisplayName("record com valores null em promotion")
    void record_nullPromotion() {
        MoveRequest req = new MoveRequest("fen", "e2", "e4", null);
        assertThat(req.promotion()).isNull();
    }

    @Test
    @DisplayName("dois records com mesmos valores são iguais")
    void record_equalityByValue() {
        MoveRequest r1 = new MoveRequest("fen", "e2", "e4", "q");
        MoveRequest r2 = new MoveRequest("fen", "e2", "e4", "q");
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    @DisplayName("hashCode é igual para records equivalentes")
    void record_hashCodeEquality() {
        MoveRequest r1 = new MoveRequest("fen", "e2", "e4", null);
        MoveRequest r2 = new MoveRequest("fen", "e2", "e4", null);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("toString contém valores dos campos")
    void record_toString() {
        MoveRequest req = new MoveRequest("fen", "e2", "e4", "q");
        String str = req.toString();
        assertThat(str).contains("e2").contains("e4").contains("q");
    }
}
