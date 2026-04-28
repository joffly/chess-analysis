package com.chess.analyzer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PartidaEntity — testes de domínio")
class PartidaEntityDomainTest {

    private Map<String, String> baseTags() {
        return new LinkedHashMap<>();
    }

    @Test
    @DisplayName("addLance e getLances em sequência")
    void addLance_andGetLances() {
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", baseTags(), "fen");
        LanceEntity lance1 = new LanceEntity(1, 1, true, "e2e4", "e4", "fen1", "fen2");
        LanceEntity lance2 = new LanceEntity(2, 1, false, "e7e5", "e5", "fen2", "fen3");
        
        partida.addLance(lance1);
        partida.addLance(lance2);
        
        assertThat(partida.getLances()).hasSize(2);
    }

    @Test
    @DisplayName("addLance associa partida corretamente")
    void addLance_associatesPartida() {
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", baseTags(), "fen");
        LanceEntity lance = new LanceEntity(1, 1, true, "e2e4", "e4", "fen1", "fen2");
        
        partida.addLance(lance);
        
        assertThat(lance.getPartida()).isEqualTo(partida);
    }

    @Test
    @DisplayName("addLance múltiplos sem remover anteriores")
    void addLance_multiplePreservesPrevious() {
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", baseTags(), "fen");
        LanceEntity l1 = new LanceEntity(1, 1, true, "e2e4", "e4", "f1", "f2");
        LanceEntity l2 = new LanceEntity(2, 1, false, "e7e5", "e5", "f2", "f3");
        LanceEntity l3 = new LanceEntity(3, 2, true, "g1f3", "Nf3", "f3", "f4");
        
        partida.addLance(l1);
        partida.addLance(l2);
        partida.addLance(l3);
        
        assertThat(partida.getLances()).containsExactly(l1, l2, l3);
    }

    @Test
    @DisplayName("corrigirResultado altera somente resultado")
    void corrigirResultado_onlyChangesResult() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "A");
        tags.put("Black", "B");
        tags.put("Result", "1-0");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        partida.corrigirResultado("0-1");
        
        assertThat(partida.getResult()).isEqualTo("0-1");
        assertThat(partida.getWhite()).isEqualTo("A");
        assertThat(partida.getBlack()).isEqualTo("B");
    }

    @Test
    @DisplayName("corrigirResultado para empate")
    void corrigirResultado_toDrawResult() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Result", "1-0");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        partida.corrigirResultado("1/2-1/2");
        
        assertThat(partida.getResult()).isEqualTo("1/2-1/2");
    }

    @Test
    @DisplayName("titulo sem jogadores conhecidos")
    void titulo_unknownPlayers() {
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", baseTags(), "fen");
        
        assertThat(partida.titulo()).isEqualTo("? vs ?");
    }

    @Test
    @DisplayName("titulo com apenas White")
    void titulo_onlyWhite() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "Player");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(partida.titulo()).isEqualTo("Player vs ?");
    }

    @Test
    @DisplayName("titulo com apenas Black")
    void titulo_onlyBlack() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Black", "Player");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(partida.titulo()).isEqualTo("? vs Player");
    }

    @Test
    @DisplayName("titulo com ambos os jogadores")
    void titulo_bothPlayers() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "Carlsen");
        tags.put("Black", "Kasparov");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(partida.titulo()).isEqualTo("Carlsen vs Kasparov");
    }

    @Test
    @DisplayName("titulo com White, Black, Event e Date")
    void titulo_allDetails() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "A");
        tags.put("Black", "B");
        tags.put("Event", "Championship");
        tags.put("Date", "2024.01.01");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        String titulo = partida.titulo();
        assertThat(titulo).contains("A vs B").contains("Championship").contains("2024.01.01");
    }

    @Test
    @DisplayName("titulo sem Event e Date")
    void titulo_noEventDate() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", "A");
        tags.put("Black", "B");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(partida.titulo()).isEqualTo("A vs B");
    }

    @Test
    @DisplayName("parseDate com múltiplos formatos")
    void parseDate_multipleValidFormats() {
        Map<String, String> t1 = new LinkedHashMap<>();
        t1.put("Date", "2024.01.01");
        PartidaEntity p1 = new PartidaEntity(0, "test.pgn", "id1", t1, "fen");
        
        Map<String, String> t2 = new LinkedHashMap<>();
        t2.put("Date", "2020.12.31");
        PartidaEntity p2 = new PartidaEntity(0, "test.pgn", "id2", t2, "fen");
        
        assertThat(p1.getDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(p2.getDate()).isEqualTo(LocalDate.of(2020, 12, 31));
    }

    @Test
    @DisplayName("parseIntOrNull com positive e negative")
    void parseIntOrNull_positiveAndNegative() {
        Map<String, String> tags1 = new LinkedHashMap<>();
        tags1.put("WhiteRatingDiff", "+50");
        PartidaEntity p1 = new PartidaEntity(0, "test.pgn", "id1", tags1, "fen");
        
        Map<String, String> tags2 = new LinkedHashMap<>();
        tags2.put("BlackRatingDiff", "-50");
        PartidaEntity p2 = new PartidaEntity(0, "test.pgn", "id2", tags2, "fen");
        
        assertThat(p1.getWhiteRatingDiff()).isEqualTo(50);
        assertThat(p2.getBlackRatingDiff()).isEqualTo(-50);
    }

    @Test
    @DisplayName("getFontePgn retorna valor exato")
    void getFontePgn_exactValue() {
        String fonte = "/path/to/games.pgn";
        PartidaEntity partida = new PartidaEntity(0, fonte, "id", baseTags(), "fen");
        
        assertThat(partida.getFontePgn()).isEqualTo(fonte);
    }

    @Test
    @DisplayName("getGameId retorna valor exato")
    void getGameId_exactValue() {
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "my-game-id", baseTags(), "fen");
        
        assertThat(partida.getGameId()).isEqualTo("my-game-id");
    }

    @Test
    @DisplayName("getPgnIndex retorna valor exato")
    void getPgnIndex_exactValue() {
        PartidaEntity partida = new PartidaEntity(42, "test.pgn", "id", baseTags(), "fen");
        
        assertThat(partida.getPgnIndex()).isEqualTo(42);
    }

    @Test
    @DisplayName("getInitialFen retorna FEN exato")
    void getInitialFen_exactValue() {
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", baseTags(), fen);
        
        assertThat(partida.getInitialFen()).isEqualTo(fen);
    }

    @Test
    @DisplayName("getEco retorna ECO code")
    void getEco_returnsEcoCode() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("ECO", "C20");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(partida.getEco()).isEqualTo("C20");
    }

    @Test
    @DisplayName("getOpening retorna nome da abertura")
    void getOpening_returnsOpeningName() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Opening", "Italian Game");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(partida.getOpening()).isEqualTo("Italian Game");
    }

    @Test
    @DisplayName("getTimeControl retorna controle de tempo")
    void getTimeControl_returnsTimeControl() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("TimeControl", "300+3");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(partida.getTimeControl()).isEqualTo("300+3");
    }

    @Test
    @DisplayName("getTermination retorna razão de término")
    void getTermination_returnsTermination() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Termination", "Checkmate");
        PartidaEntity partida = new PartidaEntity(0, "test.pgn", "id", tags, "fen");
        
        assertThat(partida.getTermination()).isEqualTo("Checkmate");
    }
}
