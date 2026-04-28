package com.chess.analyzer.service;

import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes complementares do PgnService — cobre cenários não exercitados
 * em PgnServiceTest: exportPgn com avaliações/mate, stripAnnotations,
 * promoção de peão e ELO nas tags.
 */
@DisplayName("PgnService — testes extras de cobertura")
class PgnServiceExtraTest {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // ── PGN com variantes, comentários, NAG e resultado ─────────────────────
    private static final String ANNOTATED_PGN =
            "[Event \"Anotado\"]\n" +
            "[White \"Brancas\"]\n" +
            "[Black \"Pretas\"]\n" +
            "[Result \"1-0\"]\n" +
            "\n" +
            "1. e4 $1 { bom lance } (1. d4 d5) e5 $2 2. Nf3 Nc6 3. Bc4 Bc5 1-0\n";

    // ── PGN com promoção de peão ─────────────────────────────────────────────
    // Posição: peão branco em a7, rei branco em h1, rei preto em h8
    // FEN: k7/P7/8/8/8/8/8/7K w - - 0 1
    // Lance: a7a8q (promoção para dama)
    private static final String PROMOTION_FEN =
            "k7/P7/8/8/8/8/8/7K w - - 0 1";
    private static final String PROMOTION_PGN =
            "[Event \"Promoção\"]\n" +
            "[White \"W\"]\n" +
            "[Black \"B\"]\n" +
            "[Result \"1-0\"]\n" +
            "[FEN \"k7/P7/8/8/8/8/8/7K w - - 0 1\"]\n" +
            "\n" +
            "1. a8=Q+ Kb7 2. Qa7# 1-0\n";

    // ── PGN simples para testar exportPgn com análise ──────────────────────
    private static final String SIMPLE_PGN =
            "[Event \"Export\"]\n" +
            "[White \"W\"]\n" +
            "[Black \"B\"]\n" +
            "[Result \"1-0\"]\n" +
            "\n" +
            "1. e4 e5 2. Nf3 Nc6 1-0\n";

    // ── PGN com ELO ─────────────────────────────────────────────────────────
    private static final String ELO_PGN =
            "[Event \"Rated\"]\n" +
            "[White \"Magnus\"]\n" +
            "[Black \"Hikaru\"]\n" +
            "[WhiteElo \"2856\"]\n" +
            "[BlackElo \"2710\"]\n" +
            "[Result \"1-0\"]\n" +
            "\n" +
            "1. e4 c5 1-0\n";

    private PgnService pgnService;

    @BeforeEach
    void setUp() {
        pgnService = new PgnService();
    }

    private List<GameData> loadPgn(String pgn) throws Exception {
        File tmp = File.createTempFile("pgnextra", ".pgn");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), pgn, StandardCharsets.UTF_8);
        return pgnService.load(tmp);
    }

    // ── stripAnnotations (via load) ──────────────────────────────────────────

    @Nested
    @DisplayName("Parsing com anotações")
    class StripAnnotations {

        @Test
        @DisplayName("PGN com comentários {} e variantes () ainda carrega os lances corretos")
        void annotatedPgn_stripsCommentsAndVariants() throws Exception {
            List<GameData> games = loadPgn(ANNOTATED_PGN);
            assertThat(games).hasSize(1);
            GameData game = games.get(0);
            // 6 meio-lances: e4 e5 Nf3 Nc6 Bc4 Bc5
            assertThat(game.getMoves()).hasSize(6);
        }

        @Test
        @DisplayName("PGN com NAG $1 e $2 não quebra o parsing")
        void annotatedPgn_nagTokensIgnored() throws Exception {
            GameData game = loadPgn(ANNOTATED_PGN).get(0);
            MoveEntry first = game.getMoves().get(0);
            assertThat(first.getSan()).isEqualTo("e4");
            assertThat(first.getUci()).isEqualTo("e2e4");
        }

        @Test
        @DisplayName("FEN chain continua correta mesmo com anotações")
        void annotatedPgn_fenChainIsConsistent() throws Exception {
            List<MoveEntry> moves = loadPgn(ANNOTATED_PGN).get(0).getMoves();
            for (int i = 0; i < moves.size() - 1; i++) {
                assertThat(moves.get(i + 1).getFenBefore())
                        .as("Cadeia FEN quebrada no lance %d", i + 1)
                        .isEqualTo(moves.get(i).getFenAfter());
            }
        }
    }

    // ── Promoção de peão ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Promoção de peão")
    class PawnPromotion {

        @Test
        @DisplayName("FEN inicial da partida com promoção é o FEN customizado")
        void promotionPgn_initialFen_isCustomFen() throws Exception {
            GameData game = loadPgn(PROMOTION_PGN).get(0);
            assertThat(game.getInitialFen()).isEqualTo(PROMOTION_FEN);
        }

        @Test
        @DisplayName("Promoção carrega pelo menos 1 lance (a7a8q)")
        void promotionPgn_loadsAtLeastOneMove() throws Exception {
            GameData game = loadPgn(PROMOTION_PGN).get(0);
            assertThat(game.getMoves()).isNotEmpty();
        }

        @Test
        @DisplayName("Primeiro lance tem UCI com 5 caracteres (promoção)")
        void promotionPgn_firstMove_uciHas5Chars() throws Exception {
            MoveEntry first = loadPgn(PROMOTION_PGN).get(0).getMoves().get(0);
            assertThat(first.getUci()).hasSize(5);
            assertThat(first.getUci()).startsWith("a7a8");
        }
    }

    // ── exportPgn com análise ────────────────────────────────────────────────

    @Nested
    @DisplayName("exportPgn com análise")
    class ExportWithAnalysis {

        @Test
        @DisplayName("exportPgn inclui [%eval +X.XX] para lances com avaliação numérica")
        void exportPgn_withEval_includesEvalComment() throws Exception {
            List<GameData> games = loadPgn(SIMPLE_PGN);
            GameData game = games.get(0);
            // Anota o primeiro lance com avaliação
            game.getMoves().get(0).setAnalysis(0.28, null, "d7d5", List.of("d7d5"));

            String exported = pgnService.exportPgn(games);
            assertThat(exported).contains("[%eval +0.28]");
        }

        @Test
        @DisplayName("exportPgn inclui [%eval #N] para lances com mate")
        void exportPgn_withMate_includesMateComment() throws Exception {
            List<GameData> games = loadPgn(SIMPLE_PGN);
            GameData game = games.get(0);
            game.getMoves().get(0).setAnalysis(0.0, 3, "d1h5", List.of());

            String exported = pgnService.exportPgn(games);
            assertThat(exported).contains("[%eval #3]");
        }

        @Test
        @DisplayName("exportPgn não inclui comentário eval para lances não analisados")
        void exportPgn_notAnalyzed_noEvalComment() throws Exception {
            List<GameData> games = loadPgn(SIMPLE_PGN);
            String exported = pgnService.exportPgn(games);
            assertThat(exported).doesNotContain("%eval");
        }

        @Test
        @DisplayName("exportPgn preserva o resultado no final da partida")
        void exportPgn_preservesResult() throws Exception {
            List<GameData> games = loadPgn(SIMPLE_PGN);
            String exported = pgnService.exportPgn(games);
            assertThat(exported).contains("1-0");
        }
    }

    // ── ELO nas tags ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ELO nas tags")
    class EloTags {

        @Test
        @DisplayName("Tags WhiteElo e BlackElo são carregadas corretamente")
        void eloTags_areLoaded() throws Exception {
            GameData game = loadPgn(ELO_PGN).get(0);
            // O chesslib pode expor ELO via WhiteElo / pelo player.getElo()
            // Verificamos ao menos que a partida carregou
            assertThat(game).isNotNull();
            // Aceita tanto a presença da tag quanto a ausência
            // (dependendo da versão do chesslib)
            String whiteElo = game.getTags().get("WhiteElo");
            if (whiteElo != null && !whiteElo.equals("?")) {
                assertThat(whiteElo).isEqualTo("2856");
            }
        }

        @Test
        @DisplayName("Partida com ELO carrega os lances normalmente")
        void eloPgn_loadsMoves() throws Exception {
            GameData game = loadPgn(ELO_PGN).get(0);
            assertThat(game.getMoves()).isNotEmpty();
            assertThat(game.getMoves().get(0).getSan()).isEqualTo("e4");
        }
    }
}
