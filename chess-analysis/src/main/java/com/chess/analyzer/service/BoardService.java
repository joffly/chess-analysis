package com.chess.analyzer.service;

import com.chess.analyzer.model.LegalMovesResponse;
import com.chess.analyzer.model.MoveRequest;
import com.chess.analyzer.model.MoveResponse;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Serviço que valida movimentos do usuário no tabuleiro interativo
 * e retorna os movimentos legais para uma peça em determinado quadrado.
 *
 * Usa a biblioteca chesslib para geração de movimentos legais e
 * validação de regras (xeque, xeque-mate, afogamento, promoção, roque, en-passant).
 */
@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    /**
     * Retorna os destinos legais para a peça no quadrado {@code from}
     * a partir da posição descrita pelo {@code fen}.
     */
    public LegalMovesResponse legalMoves(String fen, String from) {
        Board board = new Board();
        board.loadFromFen(fen);

        Square fromSq;
        try {
            fromSq = Square.valueOf(from.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return new LegalMovesResponse(from, List.of(), false);
        }

        List<String> targets   = new ArrayList<>();
        boolean      promotion = false;

        for (Move move : board.legalMoves()) {
            if (!move.getFrom().equals(fromSq)) continue;
            targets.add(move.getTo().value().toLowerCase(Locale.ROOT));
            if (move.getPromotion() != null && move.getPromotion() != Piece.NONE) {
                promotion = true;
            }
        }

        return new LegalMovesResponse(from, targets.stream().distinct().toList(), promotion);
    }

    /**
     * Tenta executar o lance descrito por {@link MoveRequest} e retorna
     * o novo estado do tabuleiro.
     *
     * @param req lance enviado pelo frontend
     * @return {@link MoveResponse} com o novo FEN ou mensagem de erro
     */
    public MoveResponse applyMove(MoveRequest req) {
        Board board = new Board();
        try {
            board.loadFromFen(req.fen());
        } catch (Exception e) {
            return MoveResponse.error("FEN inválida: " + e.getMessage());
        }

        Square fromSq, toSq;
        try {
            fromSq = Square.valueOf(req.from().toUpperCase(Locale.ROOT));
            toSq   = Square.valueOf(req.to()  .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MoveResponse.error("Quadrado inválido: " + req.from() + " → " + req.to());
        }

        // Identifica o lance legal correspondente
        Move matched = null;
        for (Move move : board.legalMoves()) {
            if (!move.getFrom().equals(fromSq) || !move.getTo().equals(toSq)) continue;

            // Se for promoção, filtra pela peça desejada
            if (move.getPromotion() != null && move.getPromotion() != Piece.NONE) {
                String promoChar = req.promotion() != null
                        ? req.promotion().toLowerCase(Locale.ROOT) : "q";
                String pieceChar = move.getPromotion().getFenSymbol().toLowerCase(Locale.ROOT);
                if (!pieceChar.equals(promoChar)) continue;
            }
            matched = move;
            break;
        }

        if (matched == null) {
            log.debug("Lance ilegal: {} → {} na posição {}", req.from(), req.to(), req.fen());
            return MoveResponse.error("Lance ilegal: " + req.from() + req.to());
        }

        // Constrói SAN antes de executar (chesslib suporta via toString do Move pós-execução)
        String san = buildSan(board, matched);

        boolean executed = board.doMove(matched);
        if (!executed) {
            return MoveResponse.error("Falha ao executar o lance internamente.");
        }

        String  newFen     = board.getFen();
        boolean inCheck    = board.isKingAttacked();
        boolean checkmate  = board.isMated();
        boolean stalemate  = board.isStaleMate();
        boolean gameOver   = checkmate || stalemate
                || board.isInsufficientMaterial()
                || board.getHalfMoveCounter() >= 100;

        // Adiciona símbolo de xeque/mate ao SAN
        if      (checkmate) san += "#";
        else if (inCheck)   san += "+";

        String uci = req.from() + req.to()
                + (matched.getPromotion() != null && matched.getPromotion() != Piece.NONE
                   ? matched.getPromotion().getFenSymbol().toLowerCase(Locale.ROOT) : "");

        log.debug("Lance executado: {} ({}) → newFEN={}", uci, san, newFen);

        return new MoveResponse(true, newFen, san, uci,
                inCheck, checkmate, stalemate, gameOver, null);
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Gera uma SAN simples para o lance antes de ser executado no tabuleiro.
     * Não cobre todos os casos (xeque/mate são adicionados depois de doMove),
     * mas cobre peças, roque, captura, promoção e desambiguação básica.
     */
    private String buildSan(Board board, Move move) {
        Square from  = move.getFrom();
        Square to    = move.getTo();
        Piece  piece = board.getPiece(from);

        if (piece == null || piece == Piece.NONE) return from.value().toLowerCase() + to.value().toLowerCase();

        PieceType type = piece.getPieceType();

        // Roque
        if (type == PieceType.KING) {
            int fileDiff = to.getFile().ordinal() - from.getFile().ordinal();
            if (fileDiff == 2)  return "O-O";
            if (fileDiff == -2) return "O-O-O";
        }

        StringBuilder sb = new StringBuilder();

        // Letra da peça (peão não tem letra)
        if (type != PieceType.PAWN) sb.append(type.getSanSymbol());

        // Desambiguação simples: se duas peças do mesmo tipo podem ir para o mesmo destino
        if (type != PieceType.PAWN) {
            boolean needFile = false, needRank = false;
            for (Move other : board.legalMoves()) {
                if (other.equals(move)) continue;
                if (!other.getTo().equals(to)) continue;
                Piece otherPiece = board.getPiece(other.getFrom());
                if (otherPiece == null || otherPiece.getPieceType() != type) continue;
                if (other.getFrom().getFile() == from.getFile()) needRank = true;
                else needFile = true;
            }
            if (needFile) sb.append(from.getFile().getNotation().toLowerCase(Locale.ROOT));
            if (needRank) sb.append(from.getRank().getNotation().toLowerCase(Locale.ROOT));
        } else {
            // Peão: se captura, inclui coluna de origem
            boolean isCapture = board.getPiece(to) != Piece.NONE
                    || board.getEnPassant() == to;
            if (isCapture)
                sb.append(from.getFile().getNotation().toLowerCase(Locale.ROOT));
        }

        // Captura
        boolean isCapture = board.getPiece(to) != Piece.NONE || board.getEnPassant() == to;
        if (isCapture) sb.append("x");

        // Destino
        sb.append(to.value().toLowerCase(Locale.ROOT));

        // Promoção
        if (move.getPromotion() != null && move.getPromotion() != Piece.NONE) {
            sb.append("=").append(
                    move.getPromotion().getPieceType().getSanSymbol().toUpperCase(Locale.ROOT));
        }

        return sb.toString();
    }
}
