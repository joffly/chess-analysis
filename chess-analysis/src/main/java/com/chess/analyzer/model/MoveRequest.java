package com.chess.analyzer.model;

/**
 * Payload recebido pelo endpoint POST /api/board/move.
 * Representa um lance feito pelo usuário no tabuleiro interativo.
 */
public record MoveRequest(
        String fen,        // FEN atual do tabuleiro
        String from,       // quadrado origem, ex: "e2"
        String to,         // quadrado destino, ex: "e4"
        String promotion   // peça de promoção: "q","r","b","n" ou null
) {}
