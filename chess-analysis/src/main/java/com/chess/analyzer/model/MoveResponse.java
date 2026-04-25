package com.chess.analyzer.model;

/**
 * Resposta do endpoint POST /api/board/move.
 */
public record MoveResponse(
        boolean ok,
        String  fen,        // novo FEN após o lance (null se lance ilegal)
        String  san,        // notação SAN do lance, ex: "Nf3", "O-O", "e8=Q+"
        String  uci,        // notação UCI do lance, ex: "g1f3"
        boolean check,      // rei em xeque?
        boolean checkmate,  // xeque-mate?
        boolean stalemate,  // afogamento?
        boolean gameOver,   // partida terminada por qualquer motivo?
        String  message     // mensagem de erro se ok=false
) {
    public static MoveResponse error(String msg) {
        return new MoveResponse(false, null, null, null,
                false, false, false, false, msg);
    }
}
