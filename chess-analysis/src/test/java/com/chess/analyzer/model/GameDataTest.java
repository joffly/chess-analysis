package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GameData — testes unitários")
class GameDataTest {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private Map<String, String> baseTags(String white, String black) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", white);
        tags.put("Black", black);
        tags.put("Event", "World Championship");
        tags.put("Date", "2024.01.01");
        tags.put("Result", "1-0");
        return tags;
    }

    @Test
    @DisplayName("getTitle formata 'White vs Black — Event Date'")
    void getTitle_formatsCorrectly() {
        GameData game = new GameData(0, baseTags("Carlsen", "Nepomniachtchi"), START_FEN, List.of());
        assertThat(game.getTitle())
                .isEqualTo("Carlsen vs Nepomniachtchi — World Championship 2024.01.01");
    }

    @Test
    @DisplayName("getTitle usa '?' para jogadores ausentes")
    void getTitle_missingPlayers_usesQuestionMark() {
        GameData game = new GameData(0, Map.of(), START_FEN, List.of());
        assertThat(game.getTitle()).contains("? vs ?");
    }

    @Test
    @DisplayName("toSummary retorna totalMoves correto")
    void toSummary_totalMoves() {
        MoveEntry m = new MoveEntry("e2e4", "e4", START_FEN, START_FEN, 1, true);
        GameData game = new GameData(0, baseTags("A", "B"), START_FEN, List.of(m, m));
        assertThat(game.toSummary().totalMoves()).isEqualTo(2);
    }

    @Test
    @DisplayName("toSummary.analyzed=false antes de setFullyAnalyzed")
    void toSummary_analyzed_isFalseByDefault() {
        GameData game = new GameData(0, baseTags("A", "B"), START_FEN, List.of());
        assertThat(game.toSummary().analyzed()).isFalse();
    }

    @Test
    @DisplayName("toSummary.analyzed=true após setFullyAnalyzed(true)")
    void toSummary_analyzed_isTrueAfterSet() {
        GameData game = new GameData(0, baseTags("A", "B"), START_FEN, List.of());
        game.setFullyAnalyzed(true);
        assertThat(game.toSummary().analyzed()).isTrue();
    }

    @Test
    @DisplayName("tags são imutáveis — mutação do mapa original não afeta GameData")
    void tags_areImmutable() {
        Map<String, String> mutableTags = new LinkedHashMap<>(baseTags("A", "B"));
        GameData game = new GameData(0, mutableTags, START_FEN, List.of());
        mutableTags.put("White", "HACKED");
        assertThat(game.getTags().get("White")).isEqualTo("A");
    }

    @Test
    @DisplayName("toSummary retorna null para tags opcionais ausentes")
    void toSummary_missingOptionalTags_areNull() {
        GameData game = new GameData(0, baseTags("A", "B"), START_FEN, List.of());
        GameData.Summary summary = game.toSummary();
        assertThat(summary.whiteElo()).isNull();
        assertThat(summary.opening()).isNull();
        assertThat(summary.timeControl()).isNull();
        assertThat(summary.termination()).isNull();
    }

    @Test
    @DisplayName("toSummary retorna null para tag com valor '?'")
    void toSummary_tagWithQuestionMark_isNull() {
        Map<String, String> tags = new LinkedHashMap<>(baseTags("A", "B"));
        tags.put("WhiteElo", "?");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        assertThat(game.toSummary().whiteElo()).isNull();
    }

    @Test
    @DisplayName("toSummary.result retorna valor do tag Result")
    void toSummary_result() {
        GameData game = new GameData(0, baseTags("A", "B"), START_FEN, List.of());
        assertThat(game.toSummary().result()).isEqualTo("1-0");
    }

    @Test
    @DisplayName("getIndex retorna o índice passado no construtor")
    void getIndex_returnsConstructorValue() {
        GameData game = new GameData(3, baseTags("A", "B"), START_FEN, List.of());
        assertThat(game.getIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("getInitialFen retorna o FEN passado no construtor")
    void getInitialFen_returnsConstructorValue() {
        GameData game = new GameData(0, baseTags("A", "B"), START_FEN, List.of());
        assertThat(game.getInitialFen()).isEqualTo(START_FEN);
    }

    @Test
    @DisplayName("WhiteElo e BlackElo aparecem corretamente no Summary quando presentes")
    void toSummary_eloTags_arePopulated() {
        Map<String, String> tags = new LinkedHashMap<>(baseTags("A", "B"));
        tags.put("WhiteElo", "2882");
        tags.put("BlackElo", "2793");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        assertThat(game.toSummary().whiteElo()).isEqualTo("2882");
        assertThat(game.toSummary().blackElo()).isEqualTo("2793");
    }
}
