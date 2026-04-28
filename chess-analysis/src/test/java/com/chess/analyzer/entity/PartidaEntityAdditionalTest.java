package com.chess.analyzer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PartidaEntity — testes adicionais")
class PartidaEntityAdditionalTest {

    private Map<String, String> baseTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Event", "Event");
        tags.put("Site", "Site");
        tags.put("Date", "2024.01.01");
        tags.put("Round", "1");
        tags.put("White", "White");
        tags.put("Black", "Black");
        tags.put("Result", "1-0");
        return tags;
    }

    @Test
    @DisplayName("constructor com tags vazio")
    void constructor_emptyTags() {
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", new HashMap<>(), "fen");
        
        assertThat(entity.getWhite()).isNull();
        assertThat(entity.getBlack()).isNull();
        assertThat(entity.getEvent()).isNull();
    }

    @Test
    @DisplayName("constructor com diferentes pgnIndex")
    void constructor_differentPgnIndex() {
        Map<String, String> tags = baseTags();
        PartidaEntity e1 = new PartidaEntity(0, "test.pgn", "id1", tags, "fen");
        PartidaEntity e2 = new PartidaEntity(100, "test.pgn", "id2", tags, "fen");
        
        assertThat(e1.getPgnIndex()).isEqualTo(0);
        assertThat(e2.getPgnIndex()).isEqualTo(100);
    }

    @Test
    @DisplayName("getInitialFen retorna o FEN do construtor")
    void getInitialFen_returnsConstructorFen() {
        String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", baseTags(), fen);
        
        assertThat(entity.getInitialFen()).isEqualTo(fen);
    }

    @Test
    @DisplayName("getFontePgn retorna a fonte do construtor")
    void getFontePgn_returnsConstructorSource() {
        PartidaEntity entity = new PartidaEntity(0, "myfile.pgn", "id", baseTags(), "fen");
        
        assertThat(entity.getFontePgn()).isEqualTo("myfile.pgn");
    }

    @Test
    @DisplayName("parseDate com formato PGN válido")
    void parseDate_validPgnFormat() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Date", "2020.12.31");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getDate()).isEqualTo(LocalDate.of(2020, 12, 31));
    }

    @Test
    @DisplayName("parseDate com data inválida retorna null")
    void parseDate_invalidDate_returnsNull() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Date", "2020.13.32");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getDate()).isNull();
    }

    @Test
    @DisplayName("parseDate com string vazia retorna null")
    void parseDate_emptyString_returnsNull() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Date", "");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getDate()).isNull();
    }

    @Test
    @DisplayName("parseUtcDatetime com valores válidos")
    void parseUtcDatetime_validValues() {
        Map<String, String> tags = new HashMap<>();
        tags.put("UTCDate", "2024.06.15");
        tags.put("UTCTime", "12:30:45");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getUtcDatetime().getYear()).isEqualTo(2024);
        assertThat(entity.getUtcDatetime().getMonthValue()).isEqualTo(6);
        assertThat(entity.getUtcDatetime().getDayOfMonth()).isEqualTo(15);
        assertThat(entity.getUtcDatetime().getHour()).isEqualTo(12);
        assertThat(entity.getUtcDatetime().getMinute()).isEqualTo(30);
        assertThat(entity.getUtcDatetime().getSecond()).isEqualTo(45);
    }

    @Test
    @DisplayName("parseUtcDatetime sem UTCTime retorna null")
    void parseUtcDatetime_missingTime_returnsNull() {
        Map<String, String> tags = new HashMap<>();
        tags.put("UTCDate", "2024.06.15");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getUtcDatetime()).isNull();
    }

    @Test
    @DisplayName("parseUtcDatetime com UTCDate contendo ? retorna null")
    void parseUtcDatetime_dateWithQuestionMark_returnsNull() {
        Map<String, String> tags = new HashMap<>();
        tags.put("UTCDate", "?.?.??");
        tags.put("UTCTime", "12:30:45");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getUtcDatetime()).isNull();
    }

    @Test
    @DisplayName("parseIntOrNull com string não numérica retorna null")
    void parseIntOrNull_nonNumeric_returnsNull() {
        Map<String, String> tags = new HashMap<>();
        tags.put("WhiteElo", "abc");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getWhiteElo()).isNull();
    }

    @Test
    @DisplayName("parseIntOrNull com espaços antes e depois")
    void parseIntOrNull_withSpaces() {
        Map<String, String> tags = new HashMap<>();
        tags.put("WhiteElo", "  2500  ");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getWhiteElo()).isEqualTo(2500);
    }

    @Test
    @DisplayName("titulo com todos os campos vazios")
    void titulo_allFieldsEmpty() {
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", new HashMap<>(), "fen");
        
        String title = entity.titulo();
        assertThat(title).isEqualTo("? vs ?");
    }

    @Test
    @DisplayName("titulo com Event e Date vazios")
    void titulo_eventAndDateEmpty() {
        Map<String, String> tags = new HashMap<>();
        tags.put("White", "Alice");
        tags.put("Black", "Bob");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        String title = entity.titulo();
        assertThat(title).isEqualTo("Alice vs Bob");
    }

    @Test
    @DisplayName("addLance múltiplas vezes")
    void addLance_multipleTimes() {
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", baseTags(), "fen");
        LanceEntity l1 = new LanceEntity(1, 1, true, "e2e4", "e4", "fen1", "fen2");
        LanceEntity l2 = new LanceEntity(2, 1, false, "e7e5", "e5", "fen2", "fen3");
        
        entity.addLance(l1);
        entity.addLance(l2);
        
        assertThat(entity.getLances()).hasSize(2);
        assertThat(entity.getLances().get(0).getPartida()).isEqualTo(entity);
        assertThat(entity.getLances().get(1).getPartida()).isEqualTo(entity);
    }

    @Test
    @DisplayName("corrigirResultado muda o resultado")
    void corrigirResultado_changesResult() {
        Map<String, String> tags = baseTags();
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getResult()).isEqualTo("1-0");
        entity.corrigirResultado("1/2-1/2");
        assertThat(entity.getResult()).isEqualTo("1/2-1/2");
    }

    @Test
    @DisplayName("getWhiteRatingDiff e getBlackRatingDiff")
    void ratingDiff_getters() {
        Map<String, String> tags = new HashMap<>();
        tags.put("WhiteRatingDiff", "+15");
        tags.put("BlackRatingDiff", "-15");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getWhiteRatingDiff()).isEqualTo(15);
        assertThat(entity.getBlackRatingDiff()).isEqualTo(-15);
    }

    @Test
    @DisplayName("getVariant quando presente")
    void getVariant_whenPresent() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Variant", "Atomic");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getVariant()).isEqualTo("Atomic");
    }

    @Test
    @DisplayName("getTermination quando presente")
    void getTermination_whenPresent() {
        Map<String, String> tags = new HashMap<>();
        tags.put("Termination", "Checkmate");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getTermination()).isEqualTo("Checkmate");
    }

    @Test
    @DisplayName("getId retorna null antes de persistência")
    void getId_nullBeforePersist() {
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", baseTags(), "fen");
        
        assertThat(entity.getId()).isNull();
    }
}
