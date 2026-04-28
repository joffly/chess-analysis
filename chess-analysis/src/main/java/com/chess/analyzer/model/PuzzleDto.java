package com.chess.analyzer.model;

import java.util.List;

/**
 * DTO que representa um puzzle de treino gerado a partir de um lance blunder.
 *
 * @param id             ID do lance no banco
 * @param fen            FEN antes do lance blunder (posição a resolver)
 * @param blunderUci     Lance blunder jogado na partida (em UCI)
 * @param blunderSan     Lance blunder em notação SAN
 * @param bestMoveUci    Melhor lance segundo o Stockfish (UCI)
 * @param evalBefore     Avaliação antes do blunder (perspectiva brancas)
 * @param evalAfter      Avaliação após o blunder (perspectiva brancas)
 * @param whiteTurn      true se for vez das brancas jogar
 * @param moveNumber     Número do lance
 * @param gameTitle      Título da partida de origem
 * @param pv             Linha principal do Stockfish (UCI)
 */
public record PuzzleDto(
        Long id,
        String fen,
        String blunderUci,
        String blunderSan,
        String bestMoveUci,
        Double evalBefore,
        Double evalAfter,
        boolean whiteTurn,
        int moveNumber,
        String gameTitle,
        List<String> pv
) {}
