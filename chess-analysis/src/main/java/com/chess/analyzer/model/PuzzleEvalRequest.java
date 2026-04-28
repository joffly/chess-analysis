package com.chess.analyzer.model;

/**
 * Requisição de avaliação de um lance do usuário em um puzzle.
 *
 * @param fen      FEN da posição do puzzle
 * @param userMove Lance do usuário em UCI (ex: "e2e4")
 * @param bestMove Melhor lance conhecido do puzzle (UCI) — usado como referência
 */
public record PuzzleEvalRequest(
        String fen,
        String userMove,
        String bestMove
) {}
