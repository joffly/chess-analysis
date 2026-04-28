package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GameData — testes getGameId")
class GameDataGameIdTest {

    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    @DisplayName("getGameId com tag nativa GameId retorna o valor da tag")
    void getGameId_withNativeGameId() {
        Map<String, String> tags = new HashMap<>();
        tags.put("GameId", "myF6FTAy");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        assertThat(game.getGameId()).isEqualTo("myF6FTAy");
    }

    @Test
    @DisplayName("getGameId com GameId=? usa hash SHA-256")
    void getGameId_withQuestionMarkGameId_usesSha256() {
        Map<String, String> tags = new HashMap<>();
        tags.put("GameId", "?");
        tags.put("White", "Alice");
        tags.put("Black", "Bob");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String gameId = game.getGameId();
        assertThat(gameId).hasSize(64);
        assertThat(gameId).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("getGameId com GameId ausente usa hash SHA-256")
    void getGameId_withoutGameId_usesSha256() {
        Map<String, String> tags = new HashMap<>();
        tags.put("White", "White Player");
        tags.put("Black", "Black Player");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String gameId = game.getGameId();
        assertThat(gameId).hasSize(64);
    }

    @Test
    @DisplayName("getGameId com GameId branco/vazio usa hash SHA-256")
    void getGameId_withBlankGameId_usesSha256() {
        Map<String, String> tags = new HashMap<>();
        tags.put("GameId", "   ");
        tags.put("White", "Alice");
        tags.put("Black", "Bob");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String gameId = game.getGameId();
        assertThat(gameId).hasSize(64);
    }

    @Test
    @DisplayName("getGameId determinístico: mesmos dados = mesmo hash")
    void getGameId_deterministic_sameHashForSameData() {
        Map<String, String> tags1 = new HashMap<>();
        tags1.put("White", "Player1");
        tags1.put("Black", "Player2");
        tags1.put("Event", "Tournament");
        tags1.put("Date", "2024.01.01");
        
        Map<String, String> tags2 = new HashMap<>();
        tags2.put("White", "Player1");
        tags2.put("Black", "Player2");
        tags2.put("Event", "Tournament");
        tags2.put("Date", "2024.01.01");
        
        GameData game1 = new GameData(0, tags1, START_FEN, List.of());
        GameData game2 = new GameData(1, tags2, START_FEN, List.of());
        
        assertThat(game1.getGameId()).isEqualTo(game2.getGameId());
    }

    @Test
    @DisplayName("getGameId diferente para dados diferentes")
    void getGameId_differentHashForDifferentData() {
        Map<String, String> tags1 = new HashMap<>();
        tags1.put("White", "Alice");
        tags1.put("Black", "Bob");
        
        Map<String, String> tags2 = new HashMap<>();
        tags2.put("White", "Charlie");
        tags2.put("Black", "Dave");
        
        GameData game1 = new GameData(0, tags1, START_FEN, List.of());
        GameData game2 = new GameData(0, tags2, START_FEN, List.of());
        
        assertThat(game1.getGameId()).isNotEqualTo(game2.getGameId());
    }

    @Test
    @DisplayName("getGameId usa ? para campos ausentes")
    void getGameId_usesQuestionMarkForMissingFields() {
        Map<String, String> tags = new HashMap<>();
        tags.put("White", "Alice");
        GameData game = new GameData(0, tags, START_FEN, List.of());
        
        String gameId = game.getGameId();
        assertThat(gameId).hasSize(64);
        assertThat(gameId).isNotEmpty();
    }

    @Test
    @DisplayName("getGameId usa initialFen como parte do hash")
    void getGameId_usesFenInHash() {
        Map<String, String> tags = new HashMap<>();
        tags.put("White", "A");
        tags.put("Black", "B");
        
        String fen1 = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        String fen2 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        
        GameData game1 = new GameData(0, tags, fen1, List.of());
        GameData game2 = new GameData(0, tags, fen2, List.of());
        
        assertThat(game1.getGameId()).isNotEqualTo(game2.getGameId());
    }

    @Test
    @DisplayName("getGameId com GameId nativa e válida ignora hash")
    void getGameId_nativeIdTakesPrecedence() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("GameId", "lichess-id-123");
        tags.put("White", "Player");
        tags.put("Black", "Opponent");
        
        GameData game = new GameData(0, tags, START_FEN, List.of());
        assertThat(game.getGameId()).isEqualTo("lichess-id-123");
    }

    @Test
    @DisplayName("getGameId normaliza Result, Round, Site")
    void getGameId_normalizesAllSevenTagFields() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Event", "Event");
        tags.put("Site", "Site");
        tags.put("Date", "2024.01.01");
        tags.put("Round", "1");
        tags.put("White", "White");
        tags.put("Black", "Black");
        tags.put("Result", "1-0");
        
        GameData game = new GameData(0, tags, START_FEN, List.of());
        String gameId = game.getGameId();
        
        assertThat(gameId).hasSize(64);
        assertThat(gameId).isNotBlank();
    }

    @Test
    @DisplayName("getGameId trim() remove espaços da tag nativa")
    void getGameId_trimsNativeId() {
        Map<String, String> tags = new HashMap<>();
        tags.put("GameId", "  myId  ");
        
        GameData game = new GameData(0, tags, START_FEN, List.of());
        assertThat(game.getGameId()).isEqualTo("myId");
    }

    @Test
    @DisplayName("Dois GameData com mesmos tags geram mesmo ID mesmo se criados separadamente")
    void getGameId_consistencyAcrossInstances() {
        Map<String, String> tags1 = new HashMap<>();
        tags1.put("White", "A");
        tags1.put("Black", "B");
        tags1.put("Event", "E");
        
        Map<String, String> tags2 = new HashMap<>();
        tags2.put("White", "A");
        tags2.put("Black", "B");
        tags2.put("Event", "E");
        
        GameData game1 = new GameData(0, tags1, START_FEN, List.of());
        GameData game2 = new GameData(0, tags2, START_FEN, List.of());
        
        assertThat(game1.getGameId()).isEqualTo(game2.getGameId());
    }
}
