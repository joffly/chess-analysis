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
 * @param pvLines      linhas PV adicionais do modo MultiPV (vazio para análise simples)
 */
public record StockfishResult(
        Double       eval,
        Integer      mateIn,
        String       bestMove,
        List<String> pv,
        String       fenAfterMove,
        List<PvLine> pvLines
) {
    /** Construtor de compatibilidade sem fenAfterMove e pvLines (análise normal). */
    public StockfishResult(Double eval, Integer mateIn, String bestMove, List<String> pv) {
        this(eval, mateIn, bestMove, pv, null, List.of());
    }

    /** Construtor de compatibilidade sem pvLines. */
    public StockfishResult(Double eval, Integer mateIn, String bestMove, List<String> pv, String fenAfterMove) {
        this(eval, mateIn, bestMove, pv, fenAfterMove, List.of());
    }

    public static StockfishResult empty() {
        return new StockfishResult(0.0, null, null, List.of(), null, List.of());
    }

    /**
     * Representa uma linha PV em análise MultiPV.
     *
     * @param pvIndex índice da linha (1 = melhor, 2 = segunda melhor, etc.)
     * @param eval    avaliação em peões (perspectiva Brancas); null quando há mate forçado
     * @param mateIn  mate em N (positivo = Brancas, negativo = Pretas); null se sem mate
     * @param moves   lances UCI da variante
     */
    public record PvLine(int pvIndex, Double eval, Integer mateIn, List<String> moves) {}
}
