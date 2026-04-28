package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LegalMovesResponse — testes unitários")
class LegalMovesResponseTest {

    @Test
    @DisplayName("record com targets populado armazena corretamente")
    void record_withTargets() {
        List<String> targets = List.of("e3", "e4");
        LegalMovesResponse resp = new LegalMovesResponse("e2", targets, false);
        assertThat(resp.from()).isEqualTo("e2");
        assertThat(resp.targets()).containsExactly("e3", "e4");
        assertThat(resp.promotion()).isFalse();
    }

    @Test
    @DisplayName("record com promotion=true")
    void record_promotionTrue() {
        LegalMovesResponse resp = new LegalMovesResponse("a7", List.of("a8"), true);
        assertThat(resp.promotion()).isTrue();
    }

    @Test
    @DisplayName("record com targets vazio")
    void record_emptyTargets() {
        LegalMovesResponse resp = new LegalMovesResponse("e2", List.of(), false);
        assertThat(resp.targets()).isEmpty();
    }

    @Test
    @DisplayName("record com múltiplos targets")
    void record_multipleTargets() {
        List<String> targets = List.of("d3", "d4", "e3", "e4", "f3", "f4");
        LegalMovesResponse resp = new LegalMovesResponse("e2", targets, false);
        assertThat(resp.targets()).hasSize(6);
        assertThat(resp.targets()).containsAll(targets);
    }

    @Test
    @DisplayName("dois records com mesmos valores são iguais")
    void record_equalityByValue() {
        List<String> targets = List.of("e3", "e4");
        LegalMovesResponse r1 = new LegalMovesResponse("e2", targets, false);
        LegalMovesResponse r2 = new LegalMovesResponse("e2", targets, false);
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    @DisplayName("records diferentes não são iguais")
    void record_inequality() {
        LegalMovesResponse r1 = new LegalMovesResponse("e2", List.of("e4"), false);
        LegalMovesResponse r2 = new LegalMovesResponse("d2", List.of("d4"), false);
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    @DisplayName("hashCode é igual para records equivalentes")
    void record_hashCodeEquality() {
        List<String> targets = List.of("e3", "e4");
        LegalMovesResponse r1 = new LegalMovesResponse("e2", targets, false);
        LegalMovesResponse r2 = new LegalMovesResponse("e2", targets, false);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("toString contém valores dos campos")
    void record_toString() {
        LegalMovesResponse resp = new LegalMovesResponse("e2", List.of("e3", "e4"), true);
        String str = resp.toString();
        assertThat(str).contains("e2").contains("e3").contains("e4");
    }

    @Test
    @DisplayName("targets retorna lista contendo todos os quadrados válidos")
    void record_targetsContainsAllSquares() {
        List<String> targets = List.of("a1", "b2", "c3", "d4");
        LegalMovesResponse resp = new LegalMovesResponse("e5", targets, false);
        assertThat(resp.targets()).containsExactlyInAnyOrderElementsOf(targets);
    }
}
