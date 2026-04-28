package com.chess.analyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GameData.Summary — testes unitários")
class GameDataSummaryRecordTest {

    @Test
    @DisplayName("Summary record com todos os campos")
    void summary_allFieldsStored() {
        Map<String, String> tags = new HashMap<>();
        tags.put("White", "A");
        GameData.Summary summary = new GameData.Summary(0, "Title", 10, true, "1-0", "A", "B", 
                "2024.01.01", "Event", "2800", "2750", "+25", "-25", "Site",
                "Opening", "300+3", "Checkmate", tags);
        
        assertThat(summary.index()).isEqualTo(0);
        assertThat(summary.title()).isEqualTo("Title");
        assertThat(summary.totalMoves()).isEqualTo(10);
        assertThat(summary.analyzed()).isTrue();
        assertThat(summary.result()).isEqualTo("1-0");
    }

    @Test
    @DisplayName("Summary record com campos null")
    void summary_nullFields() {
        GameData.Summary summary = new GameData.Summary(0, "Title", 0, false, "?", "?", "?",
                null, null, null, null, null, null, null, null, null, null, new HashMap<>());
        
        assertThat(summary.whiteElo()).isNull();
        assertThat(summary.blackElo()).isNull();
        assertThat(summary.site()).isNull();
        assertThat(summary.opening()).isNull();
    }

    @Test
    @DisplayName("Summary record com totalMoves 0")
    void summary_zeroMoves() {
        GameData.Summary summary = new GameData.Summary(0, "Title", 0, false, "?", "?", "?",
                "?", "?", null, null, null, null, null, null, null, null, new HashMap<>());
        
        assertThat(summary.totalMoves()).isZero();
    }

    @Test
    @DisplayName("Summary record com analyzed true")
    void summary_analyzedTrue() {
        GameData.Summary summary = new GameData.Summary(0, "Title", 10, true, "1-0", "A", "B",
                "2024.01.01", "Event", null, null, null, null, null, null, null, null, new HashMap<>());
        
        assertThat(summary.analyzed()).isTrue();
    }

    @Test
    @DisplayName("Summary record com analyzed false")
    void summary_analyzedFalse() {
        GameData.Summary summary = new GameData.Summary(0, "Title", 5, false, "*", "?", "?",
                "?", "?", null, null, null, null, null, null, null, null, new HashMap<>());
        
        assertThat(summary.analyzed()).isFalse();
    }

    @Test
    @DisplayName("Summary record com index grande")
    void summary_largeIndex() {
        GameData.Summary summary = new GameData.Summary(1000, "Title", 0, false, "?", "?", "?",
                "?", "?", null, null, null, null, null, null, null, null, new HashMap<>());
        
        assertThat(summary.index()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Summary record com totalMoves grande")
    void summary_largeTotalMoves() {
        GameData.Summary summary = new GameData.Summary(0, "Title", 500, true, "1-0", "A", "B",
                "2024.01.01", "Event", null, null, null, null, null, null, null, null, new HashMap<>());
        
        assertThat(summary.totalMoves()).isEqualTo(500);
    }

    @Test
    @DisplayName("Summary record com result variado")
    void summary_differentResults() {
        GameData.Summary s1 = new GameData.Summary(0, "Title", 0, false, "1-0", "?", "?",
                "?", "?", null, null, null, null, null, null, null, null, new HashMap<>());
        GameData.Summary s2 = new GameData.Summary(0, "Title", 0, false, "0-1", "?", "?",
                "?", "?", null, null, null, null, null, null, null, null, new HashMap<>());
        GameData.Summary s3 = new GameData.Summary(0, "Title", 0, false, "1/2-1/2", "?", "?",
                "?", "?", null, null, null, null, null, null, null, null, new HashMap<>());
        
        assertThat(s1.result()).isEqualTo("1-0");
        assertThat(s2.result()).isEqualTo("0-1");
        assertThat(s3.result()).isEqualTo("1/2-1/2");
    }

    @Test
    @DisplayName("dois Summary records com mesmos valores são iguais")
    void summary_equality() {
        Map<String, String> tags = new HashMap<>();
        GameData.Summary s1 = new GameData.Summary(0, "Title", 10, true, "1-0", "A", "B",
                "2024.01.01", "Event", null, null, null, null, null, null, null, null, tags);
        GameData.Summary s2 = new GameData.Summary(0, "Title", 10, true, "1-0", "A", "B",
                "2024.01.01", "Event", null, null, null, null, null, null, null, null, tags);
        
        assertThat(s1).isEqualTo(s2);
    }

    @Test
    @DisplayName("Summary records diferentes não são iguais")
    void summary_inequality() {
        GameData.Summary s1 = new GameData.Summary(0, "Title1", 10, true, "1-0", "A", "B",
                "2024.01.01", "Event", null, null, null, null, null, null, null, null, new HashMap<>());
        GameData.Summary s2 = new GameData.Summary(0, "Title2", 10, true, "1-0", "A", "B",
                "2024.01.01", "Event", null, null, null, null, null, null, null, null, new HashMap<>());
        
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    @DisplayName("hashCode igual para Summary records equivalentes")
    void summary_hashCode() {
        Map<String, String> tags = new HashMap<>();
        GameData.Summary s1 = new GameData.Summary(0, "Title", 10, true, "1-0", "A", "B",
                "2024.01.01", "Event", null, null, null, null, null, null, null, null, tags);
        GameData.Summary s2 = new GameData.Summary(0, "Title", 10, true, "1-0", "A", "B",
                "2024.01.01", "Event", null, null, null, null, null, null, null, null, tags);
        
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    @DisplayName("Summary record com tags contendo valores")
    void summary_tagsWithValues() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "Player1");
        tags.put("Black", "Player2");
        tags.put("Event", "Tournament");
        
        GameData.Summary summary = new GameData.Summary(0, "Title", 0, false, "?", "?", "?",
                "?", "?", null, null, null, null, null, null, null, null, tags);
        
        assertThat(summary.tags()).containsEntry("White", "Player1");
        assertThat(summary.tags()).containsEntry("Black", "Player2");
    }

    @Test
    @DisplayName("Summary record com timeControl")
    void summary_timeControl() {
        GameData.Summary summary = new GameData.Summary(0, "Title", 0, false, "?", "?", "?",
                "?", "?", null, null, null, null, null, null, "300+3", null, new HashMap<>());
        
        assertThat(summary.timeControl()).isEqualTo("300+3");
    }

    @Test
    @DisplayName("Summary record com termination")
    void summary_termination() {
        GameData.Summary summary = new GameData.Summary(0, "Title", 0, false, "?", "?", "?",
                "?", "?", null, null, null, null, null, null, null, "Checkmate", new HashMap<>());
        
        assertThat(summary.termination()).isEqualTo("Checkmate");
    }

    @Test
    @DisplayName("Summary record toString contém campos")
    void summary_toString() {
        GameData.Summary summary = new GameData.Summary(0, "MyTitle", 10, true, "1-0", "Player1", "Player2",
                "2024.01.01", "Event1", "2800", "2750", null, null, null, null, null, null, new HashMap<>());
        String str = summary.toString();
        
        assertThat(str).contains("MyTitle").contains("10");
    }

    @Test
    @DisplayName("Summary record com ratingDiff")
    void summary_ratingDiff() {
        GameData.Summary summary = new GameData.Summary(0, "Title", 0, false, "?", "?", "?",
                "?", "?", null, null, "+25", "-25", null, null, null, null, new HashMap<>());
        
        assertThat(summary.whiteRatingDiff()).isEqualTo("+25");
        assertThat(summary.blackRatingDiff()).isEqualTo("-25");
    }

    @Test
    @DisplayName("Summary record com todos os elos populados")
    void summary_allElos() {
        GameData.Summary summary = new GameData.Summary(0, "Title", 0, false, "?", "?", "?",
                "?", "?", "2800", "2750", "+25", "-25", "Site", "Opening", "300+3", "Checkmate", new HashMap<>());
        
        assertThat(summary.whiteElo()).isEqualTo("2800");
        assertThat(summary.blackElo()).isEqualTo("2750");
        assertThat(summary.site()).isEqualTo("Site");
        assertThat(summary.opening()).isEqualTo("Opening");
    }
}
