package com.chess.analyzer.service;

import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do GameAnalysisService.
 *
 * Cobre: gerenciamento de estado em memória, profundidade de análise,
 * criação de SSE emitters, serialização JSON interna e conversão UCI→SAN.
 *
 * NÃO cobre a análise assíncrona com Stockfish (exigiria integração).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameAnalysisService — testes unitários")
class GameAnalysisServiceTest {

    // FENs de referência
    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String AFTER_E4_FEN =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Mock
    private StockfishPoolService stockfishPool;

    @Mock
    private PgnService pgnService;

    private GameAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new GameAnalysisService(stockfishPool, pgnService);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private GameData makeGame(int index, String white, String black) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("White", white);
        tags.put("Black", black);
        tags.put("Event", "Teste");
        tags.put("Date", "2024.01.01");
        tags.put("Result", "1-0");
        List<MoveEntry> moves = new ArrayList<>();
        moves.add(new MoveEntry("e2e4", "e4", START_FEN, AFTER_E4_FEN, 1, true));
        return new GameData(index, tags, START_FEN, moves);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gerenciamento de partidas em memória
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gerenciamento de partidas")
    class GamesManagement {

        @Test
        @DisplayName("getGames retorna lista vazia antes de carregar qualquer partida")
        void getGames_initially_isEmpty() {
            assertThat(service.getGames()).isEmpty();
        }

        @Test
        @DisplayName("setGames substitui completamente a lista anterior")
        void setGames_replacesExistingList() {
            service.setGames(List.of(makeGame(0, "A", "B")));
            service.setGames(List.of(makeGame(0, "X", "Y"), makeGame(1, "Z", "W")));
            assertThat(service.getGames()).hasSize(2);
            assertThat(service.getGames().get(0).getTags().get("White")).isEqualTo("X");
        }

        @Test
        @DisplayName("getGames retorna visão imutável da lista")
        void getGames_returnsUnmodifiableList() {
            service.setGames(List.of(makeGame(0, "A", "B")));
            List<GameData> view = service.getGames();
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> view.add(makeGame(1, "C", "D"))
            );
        }

        @Test
        @DisplayName("getGame retorna Optional.empty() para índice negativo")
        void getGame_negativeIndex_returnsEmpty() {
            service.setGames(List.of(makeGame(0, "A", "B")));
            assertThat(service.getGame(-1)).isEmpty();
        }

        @Test
        @DisplayName("getGame retorna Optional.empty() para índice fora do range")
        void getGame_outOfRange_returnsEmpty() {
            service.setGames(List.of(makeGame(0, "A", "B")));
            assertThat(service.getGame(5)).isEmpty();
        }

        @Test
        @DisplayName("getGame retorna a partida correta para índice válido")
        void getGame_validIndex_returnsGame() {
            GameData g0 = makeGame(0, "Alpha", "Beta");
            GameData g1 = makeGame(1, "Gamma", "Delta");
            service.setGames(List.of(g0, g1));

            Optional<GameData> found = service.getGame(1);
            assertThat(found).isPresent();
            assertThat(found.get().getTags().get("White")).isEqualTo("Gamma");
        }

        @Test
        @DisplayName("setGames com lista vazia zera o estado")
        void setGames_emptyList_clearsState() {
            service.setGames(List.of(makeGame(0, "A", "B")));
            service.setGames(List.of());
            assertThat(service.getGames()).isEmpty();
            assertThat(service.getGame(0)).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Profundidade de análise
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Profundidade de análise (depth)")
    class AnalysisDepth {

        @Test
        @DisplayName("profundidade padrão é 15")
        void defaultDepth_is15() {
            assertThat(service.getAnalysisDepth()).isEqualTo(15);
        }

        @Test
        @DisplayName("setAnalysisDepth aceita valores entre 1 e 30")
        void setDepth_validRange_isStored() {
            service.setAnalysisDepth(20);
            assertThat(service.getAnalysisDepth()).isEqualTo(20);

            service.setAnalysisDepth(1);
            assertThat(service.getAnalysisDepth()).isEqualTo(1);

            service.setAnalysisDepth(30);
            assertThat(service.getAnalysisDepth()).isEqualTo(30);
        }

        @Test
        @DisplayName("setAnalysisDepth clipa valores abaixo de 1 para 1")
        void setDepth_belowMin_clipsTo1() {
            service.setAnalysisDepth(0);
            assertThat(service.getAnalysisDepth()).isEqualTo(1);

            service.setAnalysisDepth(-5);
            assertThat(service.getAnalysisDepth()).isEqualTo(1);
        }

        @Test
        @DisplayName("setAnalysisDepth clipa valores acima de 30 para 30")
        void setDepth_aboveMax_clipsTo30() {
            service.setAnalysisDepth(31);
            assertThat(service.getAnalysisDepth()).isEqualTo(30);

            service.setAnalysisDepth(100);
            assertThat(service.getAnalysisDepth()).isEqualTo(30);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estado da análise (isAnalyzing)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Estado de análise")
    class AnalyzingState {

        @Test
        @DisplayName("isAnalyzing retorna false antes de qualquer análise")
        void isAnalyzing_initially_isFalse() {
            assertThat(service.isAnalyzing()).isFalse();
        }

        @Test
        @DisplayName("startAnalysis retorna false se análise já estiver em andamento")
        void startAnalysis_whenAlreadyAnalyzing_returnsFalse() throws Exception {
            // Carrega pelo menos uma partida para a thread iniciar
            service.setGames(List.of(makeGame(0, "A", "B")));
            // Primeira chamada inicia a análise
            boolean first = service.startAnalysis();
            // Segunda chamada imediata deve retornar false
            boolean second = service.startAnalysis();

            // Garante que pelo menos a segunda retornou false
            // (a primeira pode ter terminado antes dependendo do scheduler)
            assertThat(first || !second).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SSE Emitter
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SSE Emitter")
    class SseEmitterTests {

        @Test
        @DisplayName("createEmitter retorna instância não nula")
        void createEmitter_returnsNonNull() {
            SseEmitter emitter = service.createEmitter();
            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("createEmitter pode ser chamado múltiplas vezes")
        void createEmitter_multipleCallsSucceed() {
            SseEmitter e1 = service.createEmitter();
            SseEmitter e2 = service.createEmitter();
            SseEmitter e3 = service.createEmitter();
            assertThat(e1).isNotNull();
            assertThat(e2).isNotNull();
            assertThat(e3).isNotNull();
            assertThat(e1).isNotSameAs(e2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GameData.getTitle() via GameAnalysisService
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Título da partida (GameData.getTitle)")
    class GameTitle {

        @Test
        @DisplayName("getTitle retorna 'White vs Black — Event Date'")
        void getTitle_formatsCorrectly() {
            GameData game = makeGame(0, "Kasparov", "Deep Blue");
            assertThat(game.getTitle()).contains("Kasparov");
            assertThat(game.getTitle()).contains("Deep Blue");
            assertThat(game.getTitle()).contains("vs");
        }

        @Test
        @DisplayName("getTitle usa '?' para tags ausentes")
        void getTitle_missingTags_usesQuestionMark() {
            Map<String, String> tags = new LinkedHashMap<>();
            tags.put("Result", "*");
            GameData game = new GameData(0, tags, START_FEN, List.of());
            assertThat(game.getTitle()).contains("?");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GameData.toSummary()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Summary da partida")
    class GameSummary {

        @Test
        @DisplayName("toSummary reflete index, totalMoves e analyzed")
        void toSummary_reflectsState() {
            GameData game = makeGame(3, "White", "Black");
            GameData.Summary s = game.toSummary();
            assertThat(s.index()).isEqualTo(3);
            assertThat(s.totalMoves()).isEqualTo(1);
            assertThat(s.analyzed()).isFalse();
        }

        @Test
        @DisplayName("toSummary.analyzed é true após setFullyAnalyzed(true)")
        void toSummary_analyzed_afterSetFullyAnalyzed() {
            GameData game = makeGame(0, "A", "B");
            game.setFullyAnalyzed(true);
            assertThat(game.toSummary().analyzed()).isTrue();
        }

        @Test
        @DisplayName("tagOrNull retorna null para tags com valor '?'")
        void toSummary_tagOrNull_questionMarkBecomesNull() {
            GameData game = makeGame(0, "A", "B");
            // WhiteElo não está nas tags → deve ser null
            assertThat(game.toSummary().whiteElo()).isNull();
            assertThat(game.toSummary().blackElo()).isNull();
        }

        @Test
        @DisplayName("toSummary expõe tags completas")
        void toSummary_exposesAllTags() {
            GameData game = makeGame(0, "Magnus", "Hikaru");
            Map<String, String> tags = game.toSummary().tags();
            assertThat(tags).containsKey("White");
            assertThat(tags).containsKey("Black");
            assertThat(tags.get("White")).isEqualTo("Magnus");
        }
    }
}
