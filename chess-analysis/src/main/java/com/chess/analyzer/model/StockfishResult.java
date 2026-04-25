package com.chess.analyzer.model;

import java.util.List;

/**
 * Resultado imutável de uma chamada de análise ao Stockfish.
 *
 * @param eval     avaliação em centipeões / 100 (perspectiva Brancas)
 * @param mateIn   mate em N (positivo = Brancas matam, negativo = Pretas matam), null se não forçado
 * @param bestMove melhor lance UCI, null se a posição já é final
 * @param pv       variante principal como lista de lances UCI
 */
public record StockfishResult(
        double       eval,
        Integer      mateIn,
        String       bestMove,
        List<String> pv
) {
    public static StockfishResult empty() {
        return new StockfishResult(0.0, null, null, List.of());
    }
}
