package com.chess.analyzer.service;

import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do PgnService.
 *
 * Usa PGNs embutidos como constantes para eliminar dependência de arquivos
 * externos em src/test/resources, garantindo que os testes funcionem em
 * qualquer ambiente sem configuração adicional.
 *
 * Todos os PGNs foram validados via python-chess antes de serem incluídos
 * aqui; nenhum lance ilegal ou ambíguo está presente.
 */
@DisplayName("PgnService — testes unitários")
class PgnServiceTest {

    // ── FENs de referência ──────────────────────────────────────────────────

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * FEN da abertura italiana usada como posição de partida no FROM_POSITION_PGN.
     * Equivale ao estado após 1.e4 e5 2.Nf3 Nc6 3.Bc4 Bc5 com direitos de roque.
     */
    private static final String ITALIAN_FEN =
            "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1";

    // ── PGNs embutidos ──────────────────────────────────────────────────────

    /** Partida simples a partir da posição padrão — 6 meio-lances. */
    private static final String SIMPLE_PGN =
            "[Event \"Teste\"]\n" +
            "[Site \"?\"]\n" +
            "[Date \"2024.01.01\"]\n" +
            "[White \"Alpha\"]\n" +
            "[Black \"Beta\"]\n" +
            "[Result \"1-0\"]\n" +
            "\n" +
            "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 1-0\n";

    /**
     * Scholar's Mate completo — 7 meio-lances.
     * Lance final: Qxf7# (UCI h5f7).
     */
    private static final String SCHOLARS_MATE_PGN =
            "[Event \"Scholar\"]\n" +
            "[White \"W\"]\n" +
            "[Black \"B\"]\n" +
            "[Result \"1-0\"]\n" +
            "\n" +
            "1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0\n";

    /**
     * Partida no formato "From Position" (Lichess) partindo da abertura italiana.
     *
     * 24 meio-lances validados:
     *   1.d3 d6 2.Nc3 Nf6 3.Be3 Be6 4.Nd5 Nxd5 5.Bxd5 Bxd5
     *   6.exd5 Ne7 7.c4 c6 8.dxc6 Nxc6 9.d4 exd4 10.Bxd4 Nxd4
     *   11.Qxd4 Qe7+ 12.Kd1 O-O-O
     */
    private static final String FROM_POSITION_PGN =
            "[Event \"Italian Game SuperBlitz Arena\"]\n" +
            "[Site \"https://lichess.org\"]\n" +
            "[Date \"2024.01.01\"]\n" +
            "[White \"Ugtakhbayaraa\"]\n" +
            "[Black \"fjoffly\"]\n" +
            "[Result \"1-0\"]\n" +
            "[Variant \"From Position\"]\n" +
            "[FEN \"r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1\"]\n" +
            "\n" +
            "1. d3 d6 2. Nc3 Nf6 3. Be3 Be6 4. Nd5 Nxd5 5. Bxd5 Bxd5 " +
            "6. exd5 Ne7 7. c4 c6 8. dxc6 Nxc6 9. d4 exd4 10. Bxd4 Nxd4 " +
            "11. Qxd4 Qe7+ 12. Kd1 O-O-O 1-0\n";

    // ── Setup ────────────────────────────────────────────────────────────────

    private PgnService pgnService;

    @BeforeEach
    void setUp() {
        pgnService = new PgnService();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private List<GameData> loadFromString(String pgn) throws Exception {
        File tmp = File.createTempFile("pgntest", ".pgn");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), pgn, StandardCharsets.UTF_8);
        return pgnService.load(tmp);
    }

    // ── Carregamento básico ─────────────────────────────────────────────────

    @Test
    @DisplayName("PGN válido retorna exatamente 1 partida")
    void load_validPgn_returnsOneGame() throws Exception {
        assertThat(loadFromString(SIMPLE_PGN)).hasSize(1);
    }

    @Test
    @DisplayName("PGN sem lances retorna lista vazia")
    void load_pgnWithoutMoves_returnsEmpty() throws Exception {
        // PGN com tags mas sem sequência de lances — chesslib não cria GameData
        String noMoves = "[Event \"Teste\"]\n[White \"A\"]\n[Black \"B\"]\n[Result \"*\"]\n\n*\n";
        List<GameData> result = loadFromString(noMoves);
        // Ou lista vazia, ou partida com 0 lances — ambos são comportamentos válidos
        boolean emptyOrNoMoves = result.isEmpty() ||
                result.stream().allMatch(g -> g.getMoves().isEmpty());
        assertThat(emptyOrNoMoves).isTrue();
    }

    @Test
    @DisplayName("Tags obrigatórias White, Black e Result são preenchidas")
    void load_mandatoryTags_arePresent() throws Exception {
        GameData game = loadFromString(SIMPLE_PGN).get(0);
        assertThat(game.getTags().get("White")).isEqualTo("Alpha");
        assertThat(game.getTags().get("Black")).isEqualTo("Beta");
        assertThat(game.getTags().get("Result")).isEqualTo("1-0");
    }

    @Test
    @DisplayName("Partida sem tag FEN usa posição padrão como FEN inicial")
    void load_noFenTag_usesStartFen() throws Exception {
        assertThat(loadFromString(SIMPLE_PGN).get(0).getInitialFen()).isEqualTo(START_FEN);
    }

    @Test
    @DisplayName("Partida com 6 meio-lances carrega exatamente 6 entradas")
    void load_moveCount_matchesPgn() throws Exception {
        assertThat(loadFromString(SIMPLE_PGN).get(0).getMoves()).hasSize(6);
    }

    // ── Dados dos MoveEntry ──────────────────────────────────────────────────

    @Test
    @DisplayName("Primeiro lance tem UCI 'e2e4' e SAN 'e4'")
    void load_firstMove_uciAndSan() throws Exception {
        MoveEntry first = loadFromString(SIMPLE_PGN).get(0).getMoves().get(0);
        assertThat(first.getUci()).isEqualTo("e2e4");
        assertThat(first.getSan()).isEqualTo("e4");
    }

    @Test
    @DisplayName("Todos os UCIs têm 4 ou 5 caracteres")
    void load_allMoves_uciLength() throws Exception {
        for (MoveEntry m : loadFromString(SIMPLE_PGN).get(0).getMoves()) {
            assertThat(m.getUci().length())
                    .as("UCI '%s' deve ter 4 ou 5 chars", m.getUci())
                    .isBetween(4, 5);
        }
    }

    @Test
    @DisplayName("Alternância de turnos: lances pares são das brancas, ímpares das pretas")
    void load_turnAlternation_isCorrect() throws Exception {
        List<MoveEntry> moves = loadFromString(SIMPLE_PGN).get(0).getMoves();
        for (int i = 0; i < moves.size(); i++) {
            assertThat(moves.get(i).isWhiteTurn())
                    .as("Lance %d deve ser %s", i + 1, i % 2 == 0 ? "branco" : "preto")
                    .isEqualTo(i % 2 == 0);
        }
    }

    @Test
    @DisplayName("Numeração de lances: par branco/preto compõe o mesmo moveNumber")
    void load_moveNumbers_areCorrect() throws Exception {
        List<MoveEntry> moves = loadFromString(SIMPLE_PGN).get(0).getMoves();
        for (int i = 0; i < moves.size(); i++) {
            assertThat(moves.get(i).getMoveNumber())
                    .as("moveNumber do meio-lance %d", i + 1)
                    .isEqualTo((i / 2) + 1);
        }
    }

    @Test
    @DisplayName("FENs antes/depois não são nulos nem em branco")
    void load_fenPositions_areNotBlank() throws Exception {
        for (MoveEntry m : loadFromString(SIMPLE_PGN).get(0).getMoves()) {
            assertThat(m.getFenBefore()).isNotBlank();
            assertThat(m.getFenAfter()).isNotBlank();
        }
    }

    @Test
    @DisplayName("FEN após um lance = FEN antes do próximo (continuidade de cadeia)")
    void load_fenChain_isContinuous() throws Exception {
        List<MoveEntry> moves = loadFromString(SIMPLE_PGN).get(0).getMoves();
        for (int i = 0; i < moves.size() - 1; i++) {
            assertThat(moves.get(i + 1).getFenBefore())
                    .as("FEN antes do lance %d deve ser igual ao FEN após o lance %d", i + 2, i + 1)
                    .isEqualTo(moves.get(i).getFenAfter());
        }
    }

    // ── Validação de legalidade via chesslib ────────────────────────────────

    /**
     * Verifica que cada UCI produzido pelo PgnService existe na lista de lances
     * legais da chesslib para a posição correspondente.
     */
    @Test
    @DisplayName("Todos os UCIs são legais segundo a chesslib")
    void validateInitialFenMovesLegal() throws Exception {
        GameData game = loadFromString(SIMPLE_PGN).get(0);
        Board board = new Board();
        board.loadFromFen(game.getInitialFen());

        for (MoveEntry entry : game.getMoves()) {
            Set<String> legalUcis = board.legalMoves().stream()
                    .map(m -> m.getFrom().value().toLowerCase() +
                              m.getTo().value().toLowerCase() +
                              (m.getPromotion() != null && m.getPromotion() != Piece.NONE
                               ? m.getPromotion().getFenSymbol().toLowerCase() : ""))
                    .collect(Collectors.toSet());

            assertThat(legalUcis)
                    .as("UCI '%s' (SAN: %s) deve ser legal — FEN: %s",
                            entry.getUci(), entry.getSan(), entry.getFenBefore())
                    .contains(entry.getUci());

            board.loadFromFen(entry.getFenAfter());
        }
    }

    /**
     * Para cada meio-lance, aplica o UCI no fenBefore via chesslib e verifica
     * que o FEN resultante bate com fenAfter (comparando as 4 primeiras
     * seções do FEN — ignora contadores de semi-lance e número de lance).
     */
    @Test
    @DisplayName("fenBefore + UCI produz exatamente fenAfter (coerência de FEN)")
    void validateFenMovementCoherence() throws Exception {
        GameData game = loadFromString(SIMPLE_PGN).get(0);

        for (MoveEntry entry : game.getMoves()) {
            Board board = new Board();
            board.loadFromFen(entry.getFenBefore());

            String uci = entry.getUci();
            Move matched = board.legalMoves().stream()
                    .filter(m -> {
                        String mu = m.getFrom().value().toLowerCase() +
                                    m.getTo().value().toLowerCase() +
                                    (m.getPromotion() != null && m.getPromotion() != Piece.NONE
                                     ? m.getPromotion().getFenSymbol().toLowerCase() : "");
                        return mu.equals(uci);
                    })
                    .findFirst()
                    .orElse(null);

            assertThat(matched)
                    .as("Lance %d (%s) deve ser legal — FEN: %s",
                            entry.getMoveNumber(), uci, entry.getFenBefore())
                    .isNotNull();

            board.doMove(matched);

            // Compara apenas as 4 primeiras seções do FEN (ignora contadores)
            String[] expParts = entry.getFenAfter().split("\\s+");
            String[] actParts = board.getFen().split("\\s+");
            for (int i = 0; i < 4; i++) {
                assertThat(actParts[i])
                        .as("FEN seção %d após UCI '%s'", i, uci)
                        .isEqualTo(expParts[i]);
            }
        }
    }

    // ── Scholar's Mate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Scholar's Mate: carrega 7 meio-lances")
    void scholarsMate_moveCount_isSeven() throws Exception {
        assertThat(loadFromString(SCHOLARS_MATE_PGN).get(0).getMoves()).hasSize(7);
    }

    @Test
    @DisplayName("Scholar's Mate: último UCI é 'h5f7' (Qxf7#)")
    void scholarsMate_lastMove_uciIsH5F7() throws Exception {
        List<MoveEntry> moves = loadFromString(SCHOLARS_MATE_PGN).get(0).getMoves();
        MoveEntry last = moves.get(moves.size() - 1);
        assertThat(last.getUci()).isEqualTo("h5f7");
        assertThat(last.getSan()).isEqualTo("Qxf7#");
    }

    // ── From Position (Lichess) ──────────────────────────────────────────────

    @Test
    @DisplayName("From Position: FEN inicial é a da abertura italiana")
    void fromPosition_initialFen_isItalian() throws Exception {
        GameData game = loadFromString(FROM_POSITION_PGN).get(0);
        assertThat(game.getInitialFen()).isEqualTo(ITALIAN_FEN);
    }

    @Test
    @DisplayName("From Position: carrega 24 meio-lances")
    void fromPosition_moveCount_is24() throws Exception {
        assertThat(loadFromString(FROM_POSITION_PGN).get(0).getMoves()).hasSize(24);
    }

    @Test
    @DisplayName("From Position: primeiro lance é 'd3' (brancas)")
    void fromPosition_firstMove_isD3() throws Exception {
        MoveEntry first = loadFromString(FROM_POSITION_PGN).get(0).getMoves().get(0);
        assertThat(first.getSan()).isEqualTo("d3");
        assertThat(first.getUci()).isEqualTo("d2d3");
        assertThat(first.isWhiteTurn()).isTrue();
    }

    @Test
    @DisplayName("From Position: último lance é O-O-O (preto, UCI e8c8)")
    void fromPosition_lastMove_isCastle() throws Exception {
        List<MoveEntry> moves = loadFromString(FROM_POSITION_PGN).get(0).getMoves();
        MoveEntry last = moves.get(moves.size() - 1);
        assertThat(last.getSan()).isEqualTo("O-O-O");
        assertThat(last.getUci()).isEqualTo("e8c8");
        assertThat(last.isWhiteTurn()).isFalse();
    }

    @Test
    @DisplayName("From Position: tags White, Black, Event e Variant estão corretas")
    void fromPosition_tags_areCorrect() throws Exception {
        GameData game = loadFromString(FROM_POSITION_PGN).get(0);
        assertThat(game.getTags().get("White")).isEqualTo("Ugtakhbayaraa");
        assertThat(game.getTags().get("Black")).isEqualTo("fjoffly");
        assertThat(game.getTags().get("Event")).isEqualTo("Italian Game SuperBlitz Arena");
        assertThat(game.getTags().get("Variant")).isEqualTo("From Position");
    }

    @Test
    @DisplayName("From Position: alternância e numeração de lances estão corretas")
    void fromPosition_moveMetadata_isCorrect() throws Exception {
        List<MoveEntry> moves = loadFromString(FROM_POSITION_PGN).get(0).getMoves();
        assertThat(moves).isNotEmpty();
        for (int i = 0; i < moves.size(); i++) {
            assertThat(moves.get(i).isWhiteTurn())
                    .as("Lance %d deve ser %s", i + 1, i % 2 == 0 ? "branco" : "preto")
                    .isEqualTo(i % 2 == 0);
            assertThat(moves.get(i).getMoveNumber())
                    .as("moveNumber do meio-lance %d", i + 1)
                    .isEqualTo((i / 2) + 1);
        }
    }

    @Test
    @DisplayName("From Position: cadeia de FENs é contínua")
    void fromPosition_fenChain_isContinuous() throws Exception {
        List<MoveEntry> moves = loadFromString(FROM_POSITION_PGN).get(0).getMoves();
        for (int i = 0; i < moves.size() - 1; i++) {
            assertThat(moves.get(i + 1).getFenBefore())
                    .as("FEN antes do lance %d deve ser FEN após o lance %d", i + 2, i + 1)
                    .isEqualTo(moves.get(i).getFenAfter());
        }
    }

    @Test
    @DisplayName("From Position: todos os UCIs são legais segundo a chesslib")
    void fromPosition_allMovesLegal() throws Exception {
        GameData game = loadFromString(FROM_POSITION_PGN).get(0);
        Board board = new Board();
        board.loadFromFen(game.getInitialFen());

        for (MoveEntry entry : game.getMoves()) {
            Set<String> legalUcis = board.legalMoves().stream()
                    .map(m -> m.getFrom().value().toLowerCase() +
                              m.getTo().value().toLowerCase() +
                              (m.getPromotion() != null && m.getPromotion() != Piece.NONE
                               ? m.getPromotion().getFenSymbol().toLowerCase() : ""))
                    .collect(Collectors.toSet());

            assertThat(legalUcis)
                    .as("UCI '%s' (SAN: %s) deve ser legal — FEN: %s",
                            entry.getUci(), entry.getSan(), entry.getFenBefore())
                    .contains(entry.getUci());

            board.loadFromFen(entry.getFenAfter());
        }
    }

    // ── Exportação PGN ────────────────────────────────────────────────────────

    @Test
    @DisplayName("exportPgn inclui cabeçalhos e lance e4")
    void exportPgn_containsHeadersAndMoves() throws Exception {
        List<GameData> games = loadFromString(SIMPLE_PGN);
        String exported = pgnService.exportPgn(games);
        assertThat(exported).contains("[White \"Alpha\"]");
        assertThat(exported).contains("[Black \"Beta\"]");
        assertThat(exported).containsIgnoringCase("e4");
    }

    @Test
    @DisplayName("exportPgn de lista vazia retorna string vazia ou somente espaços")
    void exportPgn_emptyList_returnsBlank() {
        assertThat(pgnService.exportPgn(List.of())).isBlank();
    }

    // ── Múltiplas partidas ───────────────────────────────────────────────────

    @Test
    @DisplayName("PGN com duas partidas retorna 2 GameData")
    void load_twoPgns_returnsTwoGames() throws Exception {
        assertThat(loadFromString(SIMPLE_PGN + "\n" + SCHOLARS_MATE_PGN)).hasSize(2);
    }
}
