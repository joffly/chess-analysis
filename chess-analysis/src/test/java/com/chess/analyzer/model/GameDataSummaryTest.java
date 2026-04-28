package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GameData — testes toSummary e getTitle")
class GameDataSummaryTest {

    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    @DisplayName("toSummary retorna index correto")
    void toSummary_indexField() {
        Map<String, String> tags = new HashMap<>();
        GameData game = new GameData(42, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().index()).isEqualTo(42);
    }

    @Test
    @DisplayName("toSummary.title chama getTitle()")
    void toSummary_titleField() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "Kasparov");
        tags.put("Black", "Karpov");
        tags.put("Event", "Test");
        tags.put("Date", "2024.01.01");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String title = game.toSummary().title();
        assertThat(title).contains("Kasparov").contains("Karpov");
    }

    @Test
    @DisplayName("toSummary.totalMoves reflete tamanho da lista")
    void toSummary_totalMovesField() {
        Map<String, String> tags = new HashMap<>();
        MoveEntry m = new MoveEntry("e2e4", "e4", START_FEN, START_FEN, 1, true);
        GameData game = new GameData(0, tags, START_FEN, List.of(m, m, m));
        
        assertThat(game.toSummary().totalMoves()).isEqualTo(3);
    }

    @Test
    @DisplayName("toSummary.analyzed=false por padrão")
    void toSummary_analyzedFalseByDefault() {
        Map<String, String> tags = new HashMap<>();
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().analyzed()).isFalse();
    }

    @Test
    @DisplayName("toSummary.analyzed=true após setFullyAnalyzed(true)")
    void toSummary_analyzedTrueAfterSet() {
        Map<String, String> tags = new HashMap<>();
        GameData game = new GameData(0, tags, START_FEN, List.of());
        game.setFullyAnalyzed(true);
        
        assertThat(game.toSummary().analyzed()).isTrue();
    }

    @Test
    @DisplayName("toSummary.result retorna tag Result")
    void toSummary_resultField() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Result", "1/2-1/2");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().result()).isEqualTo("1/2-1/2");
    }

    @Test
    @DisplayName("toSummary.result retorna '*' quando ausente")
    void toSummary_resultDefaultAsterisk() {
        Map<String, String> tags = new HashMap<>();
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().result()).isEqualTo("*");
    }

    @Test
    @DisplayName("toSummary.white retorna tag White ou '?'")
    void toSummary_whiteField() {
        Map<String, String> tags = new HashMap<>();
        tags.put("White", "Player One");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().white()).isEqualTo("Player One");
    }

    @Test
    @DisplayName("toSummary.white retorna '?' quando tag ausente")
    void toSummary_whiteDefaultQuestionMark() {
        Map<String, String> tags = new HashMap<>();
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().white()).isEqualTo("?");
    }

    @Test
    @DisplayName("toSummary.black retorna tag Black ou '?'")
    void toSummary_blackField() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Black", "Player Two");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().black()).isEqualTo("Player Two");
    }

    @Test
    @DisplayName("toSummary retorna null para tags opcionais não presentes")
    void toSummary_optionalTagsNull() {
        Map<String, String> tags = new HashMap<>();
        GameData.Summary summary = new GameData(0, tags, START_FEN, List.of()).toSummary();
        
        assertThat(summary.whiteElo()).isNull();
        assertThat(summary.blackElo()).isNull();
        assertThat(summary.site()).isNull();
        assertThat(summary.opening()).isNull();
    }

    @Test
    @DisplayName("toSummary.whiteElo retorna valor numérico quando presente")
    void toSummary_whiteElo() {
        Map<String, String> tags = new HashMap<>();
        tags.put("WhiteElo", "2750");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().whiteElo()).isEqualTo("2750");
    }

    @Test
    @DisplayName("toSummary.whiteElo=null quando tag='?'")
    void toSummary_whiteEloQuestionMark() {
        Map<String, String> tags = new HashMap<>();
        tags.put("WhiteElo", "?");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().whiteElo()).isNull();
    }

    @Test
    @DisplayName("toSummary.date retorna valor do tag Date")
    void toSummary_date() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Date", "2024.05.15");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().date()).isEqualTo("2024.05.15");
    }

    @Test
    @DisplayName("toSummary.date retorna '?' quando tag ausente")
    void toSummary_dateDefaultQuestionMark() {
        Map<String, String> tags = new HashMap<>();
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().date()).isEqualTo("?");
    }

    @Test
    @DisplayName("toSummary.event retorna valor do tag Event")
    void toSummary_event() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Event", "World Championship");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().event()).isEqualTo("World Championship");
    }

    @Test
    @DisplayName("toSummary.event retorna '?' quando tag ausente")
    void toSummary_eventDefaultQuestionMark() {
        Map<String, String> tags = new HashMap<>();
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().event()).isEqualTo("?");
    }

    @Test
    @DisplayName("toSummary inclui todos os tags originais no campo tags")
    void toSummary_tagsField() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "A");
        tags.put("Black", "B");
        tags.put("Event", "E");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().tags()).containsAllEntriesOf(tags);
    }

    @Test
    @DisplayName("getTitle com Event e Date")
    void getTitle_withEventAndDate() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "Fischer");
        tags.put("Black", "Spassky");
        tags.put("Event", "Match");
        tags.put("Date", "1972.06.11");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String title = game.getTitle();
        assertThat(title).contains("Fischer").contains("Spassky")
                        .contains("Match").contains("1972.06.11");
    }

    @Test
    @DisplayName("getTitle sem Event e Date")
    void getTitle_withoutEventAndDate() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "A");
        tags.put("Black", "B");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String title = game.getTitle();
        assertThat(title).isEqualTo("A vs B");
    }

    @Test
    @DisplayName("toSummary.termination quando presente")
    void toSummary_termination() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Termination", "Checkmate");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().termination()).isEqualTo("Checkmate");
    }

    @Test
    @DisplayName("toSummary.timeControl quando presente")
    void toSummary_timeControl() {
        Map<String, String> tags = new HashMap<>();
        tags.put("TimeControl", "300+5");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.toSummary().timeControl()).isEqualTo("300+5");
    }
}
