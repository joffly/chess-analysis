package com.chess.analyzer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PartidaEntity — testes unitários")
class PartidaEntityTest {

    private Map<String, String> baseTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Event", "Test Event");
        tags.put("Site", "Test Site");
        tags.put("Date", "2024.01.01");
        tags.put("Round", "1");
        tags.put("White", "Player A");
        tags.put("Black", "Player B");
        tags.put("Result", "1-0");
        return tags;
    }

    @Test
    @DisplayName("construtor inicializa campos obrigatórios corretamente")
    void constructor_initializesRequiredFields() {
        Map<String, String> tags = baseTags();
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "game-id-1", tags, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        
        assertThat(entity.getPgnIndex()).isEqualTo(0);
        assertThat(entity.getFontePgn()).isEqualTo("test.pgn");
        assertThat(entity.getGameId()).isEqualTo("game-id-1");
        assertThat(entity.getWhite()).isEqualTo("Player A");
        assertThat(entity.getBlack()).isEqualTo("Player B");
    }

    @Test
    @DisplayName("getTags retorna tags do construtor")
    void getTags_returnsConstructorTags() {
        Map<String, String> tags = baseTags();
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "game-id", tags, "fen");
        
        assertThat(entity.getEvent()).isEqualTo("Test Event");
        assertThat(entity.getSite()).isEqualTo("Test Site");
        assertThat(entity.getRound()).isEqualTo("1");
        assertThat(entity.getResult()).isEqualTo("1-0");
    }

    @Test
    @DisplayName("parseDate converte string de data PGN para LocalDate")
    void constructor_parsesDateCorrectly() {
        Map<String, String> tags = baseTags();
        tags.put("Date", "2024.12.25");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getDate()).isEqualTo(LocalDate.of(2024, 12, 25));
    }

    @Test
    @DisplayName("parseDate com valor '?' retorna null")
    void constructor_dateWithQuestionMark_isNull() {
        Map<String, String> tags = baseTags();
        tags.put("Date", "?.??.??");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getDate()).isNull();
    }

    @Test
    @DisplayName("parseIntOrNull converte Elo corretamente")
    void constructor_parsesEloCorrectly() {
        Map<String, String> tags = baseTags();
        tags.put("WhiteElo", "2800");
        tags.put("BlackElo", "2750");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getWhiteElo()).isEqualTo(2800);
        assertThat(entity.getBlackElo()).isEqualTo(2750);
    }

    @Test
    @DisplayName("parseIntOrNull retorna null para '?'")
    void constructor_eloWithQuestionMark_isNull() {
        Map<String, String> tags = baseTags();
        tags.put("WhiteElo", "?");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getWhiteElo()).isNull();
    }

    @Test
    @DisplayName("getLances retorna lista de lances")
    void getLances_returnsEmptyListByDefault() {
        Map<String, String> tags = baseTags();
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getLances()).isEmpty();
    }

    @Test
    @DisplayName("addLance adiciona lance e associa a partida")
    void addLance_associatesWithPartida() {
        Map<String, String> tags = baseTags();
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", "fen1", "fen2");
        
        entity.addLance(lance);
        
        assertThat(entity.getLances()).hasSize(1);
        assertThat(entity.getLances().get(0).getPartida()).isEqualTo(entity);
    }

    @Test
    @DisplayName("titulo formata 'White vs Black'")
    void titulo_formatsCorrectly() {
        Map<String, String> tags = baseTags();
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        String title = entity.titulo();
        assertThat(title).contains("Player A").contains("Player B");
    }

    @Test
    @DisplayName("titulo com jogadores ausentes usa '?'")
    void titulo_missingPlayers_usesQuestionMark() {
        Map<String, String> tags = new LinkedHashMap<>();
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        String title = entity.titulo();
        assertThat(title).contains("?");
    }

    @Test
    @DisplayName("corrigirResultado altera o resultado")
    void corrigirResultado_changesResult() {
        Map<String, String> tags = baseTags();
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        assertThat(entity.getResult()).isEqualTo("1-0");
        
        entity.corrigirResultado("0-1");
        assertThat(entity.getResult()).isEqualTo("0-1");
    }

    @Test
    @DisplayName("parseUtcDatetime converte data e hora UTC corretamente")
    void constructor_parsesUtcDatetimeCorrectly() {
        Map<String, String> tags = baseTags();
        tags.put("UTCDate", "2024.01.15");
        tags.put("UTCTime", "14:30:45");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        LocalDateTime dt = entity.getUtcDatetime();
        assertThat(dt.getYear()).isEqualTo(2024);
        assertThat(dt.getMonthValue()).isEqualTo(1);
        assertThat(dt.getDayOfMonth()).isEqualTo(15);
        assertThat(dt.getHour()).isEqualTo(14);
        assertThat(dt.getMinute()).isEqualTo(30);
    }

    @Test
    @DisplayName("getLances retorna lista imutável")
    void getLances_returnsUnmodifiableList() {
        Map<String, String> tags = baseTags();
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        List<LanceEntity> lances = entity.getLances();
        
        assertThat(lances).isEmpty();
        assertThat(lances).isUnmodifiable();
    }

    @Test
    @DisplayName("construtor com rating diff")
    void constructor_parsesRatingDiff() {
        Map<String, String> tags = baseTags();
        tags.put("WhiteRatingDiff", "+25");
        tags.put("BlackRatingDiff", "-25");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getWhiteRatingDiff()).isEqualTo(25);
        assertThat(entity.getBlackRatingDiff()).isEqualTo(-25);
    }

    @Test
    @DisplayName("construtor com eco, opening, time control")
    void constructor_parsesOtherFields() {
        Map<String, String> tags = baseTags();
        tags.put("ECO", "C20");
        tags.put("Opening", "Italian Game");
        tags.put("TimeControl", "300+3");
        PartidaEntity entity = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(entity.getEco()).isEqualTo("C20");
        assertThat(entity.getOpening()).isEqualTo("Italian Game");
        assertThat(entity.getTimeControl()).isEqualTo("300+3");
    }
}
