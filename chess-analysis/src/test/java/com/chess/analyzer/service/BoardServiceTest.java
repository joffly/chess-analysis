package com.chess.analyzer.service;

import com.chess.analyzer.model.LegalMovesResponse;
import com.chess.analyzer.model.MoveRequest;
import com.chess.analyzer.model.MoveResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BoardService — testes unitários")
class BoardServiceTest {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private BoardService service;

    @BeforeEach
    void setUp() {
        service = new BoardService();
    }

    // ── legalMoves ───────────────────────────────────────────────

    @Test
    @DisplayName("Peão e2 tem exatamente e3 e e4 como destinos na posição inicial")
    void legalMoves_pawnE2_initialPosition() {
        LegalMovesResponse resp = service.legalMoves(START_FEN, "e2");

        assertThat(resp.from()).isEqualTo("e2");
        assertThat(resp.targets()).containsExactlyInAnyOrder("e3", "e4");
        assertThat(resp.promotion()).isFalse();
    }

    @Test
    @DisplayName("Quadrado vazio retorna lista de destinos vazia")
    void legalMoves_emptySquare_returnsEmpty() {
        LegalMovesResponse resp = service.legalMoves(START_FEN, "e4");

        assertThat(resp.targets()).isEmpty();
        assertThat(resp.promotion()).isFalse();
    }

    @Test
    @DisplayName("Quadrado inválido retorna lista vazia sem lançar exceção")
    void legalMoves_invalidSquare_returnsEmpty() {
        LegalMovesResponse resp = service.legalMoves(START_FEN, "z9");

        assertThat(resp.from()).isEqualTo("z9");
        assertThat(resp.targets()).isEmpty();
    }

    @Test
    @DisplayName("Cavalo g1 tem f3 e h3 como destinos na posição inicial")
    void legalMoves_knightG1_initialPosition() {
        LegalMovesResponse resp = service.legalMoves(START_FEN, "g1");

        assertThat(resp.targets()).containsExactlyInAnyOrder("f3", "h3");
    }

    @Test
    @DisplayName("Peão prestes a promover sinaliza promotion=true")
    void legalMoves_pawnAboutToPromote_flagIsTrue() {
        // Rei branco a1, rei preto h8, peão branco e7
        String fen = "7k/4P3/8/8/8/8/8/K7 w - - 0 1";
        LegalMovesResponse resp = service.legalMoves(fen, "e7");

        assertThat(resp.promotion()).isTrue();
        assertThat(resp.targets()).contains("e8");
    }

    // ── applyMove ────────────────────────────────────────────────

    @Test
    @DisplayName("Lance legal e2e4 retorna ok=true e novo FEN")
    void applyMove_legalPawnMove_returnsNewFen() {
        MoveRequest req = new MoveRequest(START_FEN, "e2", "e4", null);
        MoveResponse resp = service.applyMove(req);

        assertThat(resp.ok()).isTrue();
        assertThat(resp.fen()).isNotBlank();
        assertThat(resp.san()).isEqualTo("e4");
        assertThat(resp.uci()).isEqualTo("e2e4");
        assertThat(resp.message()).isNull();
    }

    @Test
    @DisplayName("Lance ilegal retorna ok=false com mensagem de erro")
    void applyMove_illegalMove_returnsError() {
        MoveRequest req = new MoveRequest(START_FEN, "e2", "e5", null);
        MoveResponse resp = service.applyMove(req);

        assertThat(resp.ok()).isFalse();
        assertThat(resp.message()).isNotBlank();
        assertThat(resp.fen()).isNull();
    }

    @Test
    @DisplayName("FEN inválida retorna ok=false")
    void applyMove_invalidFen_returnsError() {
        MoveRequest req = new MoveRequest("not-a-fen", "e2", "e4", null);
        MoveResponse resp = service.applyMove(req);

        assertThat(resp.ok()).isFalse();
        assertThat(resp.message()).isNotBlank();
    }

    @Test
    @DisplayName("Quadrado inválido retorna ok=false com mensagem 'inválido'")
    void applyMove_invalidSquare_returnsError() {
        MoveRequest req = new MoveRequest(START_FEN, "z9", "e4", null);
        MoveResponse resp = service.applyMove(req);

        assertThat(resp.ok()).isFalse();
        assertThat(resp.message()).containsIgnoringCase("inválido");
    }

    @Test
    @DisplayName("Scholar's Mate (Qxf7#): checkmate=true e gameOver=true")
    void applyMove_checkmate_flagsAreTrue() {
        // Posição após 1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6??
        // A dama branca está em h5, prestes a executar Qxf7#
        // FEN verificada: dama=h5, bispo=c4, peões intactos, cavalo preto em c6 e f6
        String fenBefore = "rnbqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4";
        MoveRequest req = new MoveRequest(fenBefore, "h5", "f7", null);
        MoveResponse resp = service.applyMove(req);

        assertThat(resp.ok()).isTrue();
        assertThat(resp.checkmate()).isTrue();
        assertThat(resp.gameOver()).isTrue();
        assertThat(resp.san()).endsWith("#");
    }

    @Test
    @DisplayName("Promoção de peão para rainha retorna SAN com '=Q'")
    void applyMove_pawnPromotion_queenSan() {
        String fen = "7k/4P3/8/8/8/8/8/K7 w - - 0 1";
        MoveRequest req = new MoveRequest(fen, "e7", "e8", "q");
        MoveResponse resp = service.applyMove(req);

        assertThat(resp.ok()).isTrue();
        assertThat(resp.san()).contains("=Q");
        assertThat(resp.uci()).isEqualTo("e7e8q");
    }

    @Test
    @DisplayName("Roque curto das brancas retorna SAN 'O-O'")
    void applyMove_kingsideCastle_sanOO() {
        String fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1";
        MoveRequest req = new MoveRequest(fen, "e1", "g1", null);
        MoveResponse resp = service.applyMove(req);

        assertThat(resp.ok()).isTrue();
        assertThat(resp.san()).isEqualTo("O-O");
    }

    @Test
    @DisplayName("Roque longo das brancas retorna SAN 'O-O-O'")
    void applyMove_queensideCastle_sanOOO() {
        String fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1";
        MoveRequest req = new MoveRequest(fen, "e1", "c1", null);
        MoveResponse resp = service.applyMove(req);

        assertThat(resp.ok()).isTrue();
        assertThat(resp.san()).isEqualTo("O-O-O");
    }

    @Test
    @DisplayName("Lance de captura inclui 'x' na SAN")
    void applyMove_captureMove_sanContainsX() {
        String fen = "rnbqkbnr/pppp1ppp/8/4p3/3P4/8/PPP1PPPP/RNBQKBNR w KQkq e6 0 2";
        MoveRequest req = new MoveRequest(fen, "d4", "e5", null);
        MoveResponse resp = service.applyMove(req);

        assertThat(resp.ok()).isTrue();
        assertThat(resp.san()).contains("x");
    }
}
