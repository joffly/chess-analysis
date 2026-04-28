package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GameData — testes de métodos e lógica")
class GameDataMethodsTest {

    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    @DisplayName("getMoves retorna lista imutável")
    void getMoves_isImmutable() {
        List<MoveEntry> moves = new ArrayList<>();
        GameData game = new GameData(0, new HashMap<>(), START_FEN, moves);
        
        assertThat(game.getMoves()).isUnmodifiable();
    }

    @Test
    @DisplayName("getMoves com lista vazia")
    void getMoves_empty() {
        GameData game = new GameData(0, new HashMap<>(), START_FEN, List.of());
        
        assertThat(game.getMoves()).isEmpty();
    }

    @Test
    @DisplayName("getMoves com múltiplos moves")
    void getMoves_multiple() {
        MoveEntry m1 = new MoveEntry("e2e4", "e4", START_FEN, START_FEN, 1, true);
        MoveEntry m2 = new MoveEntry("e7e5", "e5", START_FEN, START_FEN, 1, false);
        GameData game = new GameData(0, new HashMap<>(), START_FEN, List.of(m1, m2));
        
        assertThat(game.getMoves()).hasSize(2);
    }

    @Test
    @DisplayName("isFullyAnalyzed false por padrão")
    void isFullyAnalyzed_falseByDefault() {
        GameData game = new GameData(0, new HashMap<>(), START_FEN, List.of());
        
        assertThat(game.isFullyAnalyzed()).isFalse();
    }

    @Test
    @DisplayName("setFullyAnalyzed(true) muda estado")
    void setFullyAnalyzed_true() {
        GameData game = new GameData(0, new HashMap<>(), START_FEN, List.of());
        
        game.setFullyAnalyzed(true);
        assertThat(game.isFullyAnalyzed()).isTrue();
    }

    @Test
    @DisplayName("setFullyAnalyzed(false) muda estado")
    void setFullyAnalyzed_false() {
        GameData game = new GameData(0, new HashMap<>(), START_FEN, List.of());
        
        game.setFullyAnalyzed(true);
        game.setFullyAnalyzed(false);
        assertThat(game.isFullyAnalyzed()).isFalse();
    }

    @Test
    @DisplayName("getTags retorna mapa imutável")
    void getTags_isImmutable() {
        Map<String, String> tags = new HashMap<>();
        tags.put("White", "A");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.getTags()).isUnmodifiable();
    }

    @Test
    @DisplayName("getTags com tags vazio")
    void getTags_empty() {
        GameData game = new GameData(0, new HashMap<>(), START_FEN, List.of());
        
        assertThat(game.getTags()).isEmpty();
    }

    @Test
    @DisplayName("getTags não afeta o mapa original")
    void getTags_independentFromOriginal() {
        Map<String, String> tags = new HashMap<>();
        tags.put("White", "A");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        tags.put("Black", "B");
        
        assertThat(game.getTags()).doesNotContainKey("Black");
    }

    @Test
    @DisplayName("getIndex com 0")
    void getIndex_zero() {
        GameData game = new GameData(0, new HashMap<>(), START_FEN, List.of());
        
        assertThat(game.getIndex()).isZero();
    }

    @Test
    @DisplayName("getIndex com valor grande")
    void getIndex_large() {
        GameData game = new GameData(1000, new HashMap<>(), START_FEN, List.of());
        
        assertThat(game.getIndex()).isEqualTo(1000);
    }

    @Test
    @DisplayName("getInitialFen retorna FEN exato")
    void getInitialFen_exact() {
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        GameData game = new GameData(0, new HashMap<>(), fen, List.of());
        
        assertThat(game.getInitialFen()).isEqualTo(fen);
    }

    @Test
    @DisplayName("getTitle com Event e Date completos")
    void getTitle_fullDetails() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "Fischer");
        tags.put("Black", "Spassky");
        tags.put("Event", "World Championship");
        tags.put("Date", "1972.06.11");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String title = game.getTitle();
        assertThat(title).contains("Fischer").contains("Spassky")
                        .contains("World Championship").contains("1972.06.11");
    }

    @Test
    @DisplayName("getTitle com somente White e Black")
    void getTitle_onlyPlayers() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "Player1");
        tags.put("Black", "Player2");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String title = game.getTitle();
        assertThat(title).isEqualTo("Player1 vs Player2");
    }

    @Test
    @DisplayName("getTitle com tags vazio")
    void getTitle_emptyTags() {
        GameData game = new GameData(0, new HashMap<>(), START_FEN, List.of());
        
        String title = game.getTitle();
        assertThat(title).isEqualTo("? vs ?");
    }

    @Test
    @DisplayName("getTitle com Event mas sem Date")
    void getTitle_eventWithoutDate() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "A");
        tags.put("Black", "B");
        tags.put("Event", "Tournament");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String title = game.getTitle();
        assertThat(title).contains("A").contains("B").contains("Tournament");
    }

    @Test
    @DisplayName("toSummary retorna Summary com dados corretos")
    void toSummary_returnsSummary() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "A");
        tags.put("Black", "B");
        tags.put("Result", "1-0");
        MoveEntry m = new MoveEntry("e2e4", "e4", START_FEN, START_FEN, 1, true);
        GameData game = new GameData(5, tags, START_FEN, List.of(m, m));
        game.setFullyAnalyzed(true);
        
        GameData.Summary summary = game.toSummary();
        
        assertThat(summary.index()).isEqualTo(5);
        assertThat(summary.totalMoves()).isEqualTo(2);
        assertThat(summary.analyzed()).isTrue();
        assertThat(summary.white()).isEqualTo("A");
        assertThat(summary.black()).isEqualTo("B");
    }

    @Test
    @DisplayName("toSummary antes e depois de setFullyAnalyzed")
    void toSummary_beforeAndAfterAnalyzed() {
        Map<String, String> tags = new HashMap<>();
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        GameData.Summary s1 = game.toSummary();
        game.setFullyAnalyzed(true);
        GameData.Summary s2 = game.toSummary();
        
        assertThat(s1.analyzed()).isFalse();
        assertThat(s2.analyzed()).isTrue();
    }

    @Test
    @DisplayName("getTitle com espaços extras em Event")
    void getTitle_excessiveSpaces() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "A");
        tags.put("Black", "B");
        tags.put("Event", "");
        tags.put("Date", "");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String title = game.getTitle();
        assertThat(title).isEqualTo("A vs B");
    }

    @Test
    @DisplayName("getMoves com 100+ moves")
    void getMoves_manyMoves() {
        List<MoveEntry> moves = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            moves.add(new MoveEntry("e2e4", "e4", START_FEN, START_FEN, i, true));
        }
        GameData game = new GameData(0, new HashMap<>(), START_FEN, moves);
        
        assertThat(game.getMoves()).hasSize(150);
    }

    @Test
    @DisplayName("getTags com múltiplos valores")
    void getTags_multipleValues() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "Player1");
        tags.put("Black", "Player2");
        tags.put("Event", "Tournament");
        tags.put("Site", "Paris");
        tags.put("Date", "2024.01.01");
        tags.put("Round", "5");
        tags.put("Result", "1-0");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.getTags()).hasSize(7);
    }
}
