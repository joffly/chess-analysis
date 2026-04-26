package com.chess.analyzer.service;

import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PgnService — testes unitários")
class PgnServiceTest {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // PGN mínimo de uma partida completa (Partida Imortal — fragmento)
    private static final String SIMPLE_PGN =
            "[Event \"Teste\"]\n" +
            "[Site \"?\"]\n" +
            "[Date \"2024.01.01\"]\n" +
            "[White \"Alpha\"]\n" +
            "[Black \"Beta\"]\n" +
            "[Result \"1-0\"]\n" +
            "\n" +
            "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 1-0\n";

    // PGN com Scholar's Mate completo
    private static final String SCHOLARS_MATE_PGN =
            "[Event \"Scholar\"]\n" +
            "[White \"W\"]\n" +
            "[Black \"B\"]\n" +
            "[Result \"1-0\"]\n" +
            "\n" +
            "1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0\n";

    private PgnService service;

    @BeforeEach
    void setUp() {
        service = new PgnService();
    }

    // ── parseContent via File temporário ────────────────────────────────────

    private List<GameData> loadFromString(String pgn) throws Exception {
        File tmp = File.createTempFile("test", ".pgn");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), pgn, StandardCharsets.UTF_8);
        return service.load(tmp);
    }

    // ── Testes básicos de carregamento ──────────────────────────────────────

    @Test
    @DisplayName("PGN válido carrega exatamente 1 partida")
    void load_validPgn_returnsOneGame() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        assertThat(games).hasSize(1);
    }

    @Test
    @DisplayName("PGN vazio retorna lista vazia sem exceção")
    void load_emptyPgn_returnsEmpty() throws Exception {
        List<GameData> games = loadFromString("\n");
        assertThat(games).isEmpty();
    }

    @Test
    @DisplayName("Tags obrigatórias são preenchidas corretamente")
    void load_mandatoryTags_arePresent() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        GameData game = games.get(0);

        assertThat(game.getTags().get("White")).isEqualTo("Alpha");
        assertThat(game.getTags().get("Black")).isEqualTo("Beta");
        assertThat(game.getTags().get("Result")).isEqualTo("1-0");
    }

    @Test
    @DisplayName("FEN inicial é a posição padrão para partida sem tag FEN")
    void load_noFenTag_usesStartFen() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        assertThat(games.get(0).getInitialFen()).isEqualTo(START_FEN);
    }

    @Test
    @DisplayName("Número de lances carregados corresponde ao PGN")
    void load_moveCount_matchesPgn() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        // 1.e4 e5 2.Nf3 Nc6 3.Bc4 Bc5 = 6 meio-lances
        assertThat(games.get(0).getMoves()).hasSize(6);
    }

    // ── Validação de UCI dos lances carregados ──────────────────────────────

    @Test
    @DisplayName("Primeiro lance tem UCI 'e2e4'")
    void load_firstMove_uciIsE2E4() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        MoveEntry first = games.get(0).getMoves().get(0);
        assertThat(first.getUci()).isEqualTo("e2e4");
    }

    @Test
    @DisplayName("UCIs dos lances são strings de 4 ou 5 caracteres (5 para promoção)")
    void load_allMoves_uciHasCorrectLength() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        for (MoveEntry m : games.get(0).getMoves()) {
            int len = m.getUci().length();
            assertThat(len).as("UCI '%s' deve ter 4 ou 5 chars", m.getUci())
                           .isBetween(4, 5);
        }
    }

    // ── validateInitialFenMovesLegal ─────────────────────────────────────────
    // O PgnService deve produzir MoveEntry.uci legíveis pelo chesslib.
    // Verificamos que o UCI do primeiro lance (é sempre um lance das brancas
    // na posição inicial) existe de fato na lista de lances legais do Board.

    @Test
    @DisplayName("Todos os UCIs carregados são legais segundo a chesslib (verificação incremental)")
    void validateInitialFenMovesLegal() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        GameData game = games.get(0);

        Board board = new Board();
        board.loadFromFen(game.getInitialFen());

        for (MoveEntry entry : game.getMoves()) {
            // Constrói conjunto de UCIs legais para a posição atual
            Set<String> legalUcis = board.legalMoves().stream()
                    .map(m -> m.getFrom().value().toLowerCase() +
                              m.getTo().value().toLowerCase() +
                              (m.getPromotion() != null &&
                               m.getPromotion() != com.github.bhlangonijr.chesslib.Piece.NONE
                               ? m.getPromotion().getFenSymbol().toLowerCase() : ""))
                    .collect(Collectors.toSet());

            assertThat(legalUcis)
                    .as("UCI '%s' (SAN: %s) deve ser legal na posição FEN: %s",
                        entry.getUci(), entry.getSan(), entry.getFenBefore())
                    .contains(entry.getUci());

            // Avança o tabuleiro
            board.loadFromFen(entry.getFenAfter());
        }
    }

    // ── validateFenMovementCoherence ─────────────────────────────────────────
    // fenBefore aplicado com o UCI deve resultar exatamente no fenAfter.

    @Test
    @DisplayName("fenBefore + UCI resulta exatamente no fenAfter (coerência de FEN)")
    void validateFenMovementCoherence() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        GameData game = games.get(0);

        for (MoveEntry entry : game.getMoves()) {
            Board board = new Board();
            board.loadFromFen(entry.getFenBefore());

            // Localiza o Move object correspondente ao UCI
            String uci = entry.getUci();
            Move matched = board.legalMoves().stream()
                    .filter(m -> {
                        String mUci = m.getFrom().value().toLowerCase() +
                                      m.getTo().value().toLowerCase() +
                                      (m.getPromotion() != null &&
                                       m.getPromotion() != com.github.bhlangonijr.chesslib.Piece.NONE
                                       ? m.getPromotion().getFenSymbol().toLowerCase() : "");
                        return mUci.equals(uci);
                    })
                    .findFirst()
                    .orElse(null);

            assertThat(matched)
                    .as("Lance %d (%s) deve ser legal na FEN '%s'",
                        entry.getMoveNumber(), uci, entry.getFenBefore())
                    .isNotNull();

            board.doMove(matched);
            String computedFen = board.getFen();

            // Compara apenas as 4 primeiras secções do FEN (ignora contadores de
            // semi-lance e número de lance que podem diferir por convenção)
            String expectedCore = entry.getFenAfter().split("\\s+", 5)[0] + " " +
                                  entry.getFenAfter().split("\\s+", 5)[1] + " " +
                                  entry.getFenAfter().split("\\s+", 5)[2] + " " +
                                  entry.getFenAfter().split("\\s+", 5)[3];
            String computedCore = computedFen.split("\\s+", 5)[0] + " " +
                                  computedFen.split("\\s+", 5)[1] + " " +
                                  computedFen.split("\\s+", 5)[2] + " " +
                                  computedFen.split("\\s+", 5)[3];

            assertThat(computedCore)
                    .as("FEN após aplicar UCI '%s' diverge do fenAfter armazenado", uci)
                    .isEqualTo(expectedCore);
        }
    }

    // ── Scholar's Mate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Scholar's Mate: último lance tem UCI 'h5f7'")
    void scholarsMate_lastMove_uciIsH5F7() throws Exception {
        List<GameData> games = loadFromString(SCHOLARS_MATE_PGN);
        assertThat(games).hasSize(1);
        List<MoveEntry> moves = games.get(0).getMoves();
        // 4 lances das brancas + 3 lances das pretas = 7 meio-lances
        MoveEntry last = moves.get(moves.size() - 1);
        assertThat(last.getUci()).isEqualTo("h5f7");
    }

    @Test
    @DisplayName("Scholar's Mate: partida carrega 7 meio-lances")
    void scholarsMate_moveCount_isSeven() throws Exception {
        List<GameData> games = loadFromString(SCHOLARS_MATE_PGN);
        assertThat(games.get(0).getMoves()).hasSize(7);
    }

    // ── Exportação PGN ────────────────────────────────────────────────────────

    @Test
    @DisplayName("exportPgn inclui cabeçalhos e lances")
    void exportPgn_containsHeadersAndMoves() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        String exported = service.exportPgn(games);

        assertThat(exported).contains("[White \"Alpha\"]");
        assertThat(exported).contains("[Black \"Beta\"]");
        // deve conter ao menos o primeiro lance
        assertThat(exported).containsIgnoringCase("e4");
    }

    @Test
    @DisplayName("exportPgn de lista vazia retorna string vazia")
    void exportPgn_emptyList_returnsEmpty() {
        String result = service.exportPgn(List.of());
        assertThat(result).isBlank();
    }

    // ── Múltiplas partidas ───────────────────────────────────────────────────

    @Test
    @DisplayName("PGN com duas partidas carrega exatamente 2 GameData")
    void load_twoPgns_returnsTwoGames() throws Exception {
        String twoPgns = SIMPLE_PGN + "\n" + SCHOLARS_MATE_PGN;
        List<GameData> games = loadFromString(twoPgns);
        assertThat(games).hasSize(2);
    }
}
