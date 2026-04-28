package com.chess.analyzer.model;

import java.util.List;

/**
 * Resposta da avaliação do lance do usuário pelo Stockfish.
 *
 * @param good         true se o lance do usuário foi considerado bom
 * @param userEval     Avaliação após o lance do usuário (perspectiva brancas)
 * @param bestEval     Avaliação do melhor lance (perspectiva brancas)
 * @param evalDiff     Diferença de avaliação (bestEval - userEval)
 * @param userMoveSan  Lance do usuário em SAN
 * @param bestMoveSan  Melhor lance em SAN
 * @param message      Mensagem descritiva para o usuário
 * @param pv           Linha principal após o melhor lance
 */
public record PuzzleEvalResponse(
        boolean good,
        Double userEval,
        Double bestEval,
        Double evalDiff,
        String userMoveSan,
        String bestMoveSan,
        String message,
        List<String> pv
) {}
