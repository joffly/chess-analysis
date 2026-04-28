package com.chess.analyzer.model;

import java.util.List;

/**
 * Resultado imutável de uma chamada de análise ao Stockfish.
 *
 * @param eval         avaliação em centipeões / 100 (perspectiva Brancas)
 * @param mateIn       mate em N (positivo = Brancas matam, negativo = Pretas matam), null se não forçado
 * @param bestMove     melhor lance UCI, null se a posição já é final
 * @param pv           variante principal como lista de lances UCI
 * @param fenAfterMove FEN resultante após o lance aplicado (preenchido apenas em analyzeWithMove)
 */
public record StockfishResult(
        Double       eval,
        Integer      mateIn,
        String       bestMove,
        List<String> pv,
        String       fenAfterMove
) {
    /** Construtor de compatibilidade sem fenAfterMove (análise normal). */
    public StockfishResult(Double eval, Integer mateIn, String bestMove, List<String> pv) {
        this(eval, mateIn, bestMove, pv, null);
    }

    public static StockfishResult empty() {
        return new StockfishResult(0.0, null, null, List.of(), null);
    }
}
