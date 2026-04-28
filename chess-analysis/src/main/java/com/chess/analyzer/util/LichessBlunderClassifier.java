package com.chess.analyzer.util;

/**
 * Classifica lances de xadrez usando o critério exato do Lichess.
 *
 * <h2>Fórmula</h2>
 * <p>O Lichess não usa perda bruta de centipeões. Em vez disso converte
 * cada avaliação para uma probabilidade de vitória no intervalo [-1, +1]
 * via função sigmóide:</p>
 *
 * <pre>
 *   winChances(cp) = 2 / (1 + exp(-0.00368208 * cp)) - 1
 * </pre>
 *
 * <p>O delta é calculado na perspectiva do jogador que acabou de mover:<br>
 *   {@code delta = winChances(cpBefore) - winChances(cpAfter)}<br>
 *   (positivo = o jogador piorou a própria posição)</p>
 *
 * <p>Limiares (fonte: {@code lila/modules/analyse/src/main/Advice.scala}):</p>
 * <ul>
 *   <li>delta ≥ 0.30 &rarr; <strong>Blunder</strong></li>
 *   <li>delta ≥ 0.20 &rarr; Mistake</li>
 *   <li>delta ≥ 0.10 &rarr; Inaccuracy</li>
 *   <li>delta &lt;  0.10 &rarr; Good move</li>
 * </ul>
 *
 * <p>A avaliação é limitada (capped) a [-1000, +1000] centipeões antes do
 * cálculo, exatamente como o Lichess faz.</p>
 *
 * <h2>Casos especiais de mate</h2>
 * <ul>
 *   <li>O jogador deixa o adversário ter mate forçado que não existia
 *       (<em>MateCreated</em>): <strong>Blunder</strong>, exceto se já
 *       estava numa posição perdida (&lt; -700 cp = Mistake;
 *       &lt; -999 cp = Inaccuracy).</li>
 *   <li>O jogador perde um mate forçado que tinha
 *       (<em>MateLost</em>): <strong>Blunder</strong>, exceto se a
 *       posição resultante ainda for quase ganho (&gt; 999 cp = Inaccuracy;
 *       &gt; 700 cp = Mistake).</li>
 * </ul>
 *
 * @see <a href="https://github.com/lichess-org/scalachess/blob/master/core/src/main/scala/eval.scala">
 *      scalachess/eval.scala — winningChances</a>
 * @see <a href="https://github.com/lichess-org/lila/blob/master/modules/analyse/src/main/Advice.scala">
 *      lila/Advice.scala — limiares de blunder</a>
 */
public final class LichessBlunderClassifier {

    private LichessBlunderClassifier() {}

    // --- constantes do Lichess -------------------------------------------

    /** Multiplicador da sigmoid; calibrado empiricamente pelo Lichess. */
    private static final double MULTIPLIER = -0.00368208;

    /** Teto de centipeões antes de aplicar a sigmoid (igual ao Lichess). */
    private static final int CP_CEILING = 1000;

    /** Queda mínima de winning chances para classificar como blunder. */
    public static final double BLUNDER_THRESHOLD    = 0.30;
    /** Queda mínima de winning chances para classificar como mistake. */
    public static final double MISTAKE_THRESHOLD    = 0.20;
    /** Queda mínima de winning chances para classificar como inaccuracy. */
    public static final double INACCURACY_THRESHOLD = 0.10;

    // --- enum de classificação ------------------------------------------

    public enum Judgement { BLUNDER, MISTAKE, INACCURACY, GOOD }

    // --- API pública -------------------------------------------------------

    /**
     * Classifica um lance usando apenas avaliações em centipeões.
     *
     * @param cpBefore  avaliação (perspectiva brancas) <em>antes</em> do lance;
     *                  é a eval do melhor lance disponível naquela posição
     * @param cpAfter   avaliação (perspectiva brancas) <em>depois</em> do lance;
     *                  é a eval da posição resultante após o lance jogado
     * @param whiteToMove {@code true} se foi o lance das brancas
     * @return classificação do lance
     */
    public static Judgement classify(double cpBefore, double cpAfter, boolean whiteToMove) {
        double wcBefore = winningChances(cpBefore);
        double wcAfter  = winningChances(cpAfter);
        // delta na perspectiva do jogador que moveu
        double delta = whiteToMove
                ? wcBefore - wcAfter   // brancas: posição piorou se cp caiu
                : wcAfter  - wcBefore; // pretas:  posição piorou se cp subiu
        return fromDelta(delta);
    }

    /**
     * Classifica quando o lance anterior tinha mate forçado e o lance jogado
     * o perdeu (<em>MateLost</em>), usando a avaliação da posição resultante.
     *
     * @param cpAfter avaliação em centipeões da posição após o lance
     *                (perspectiva do jogador que perdeu o mate)
     * @return classificação
     */
    public static Judgement classifyMateLost(double cpAfter) {
        if (cpAfter > 999) return Judgement.INACCURACY;
        if (cpAfter > 700) return Judgement.MISTAKE;
        return Judgement.BLUNDER;
    }

    /**
     * Classifica quando o lance jogado criou um mate forçado para o adversário
     * (<em>MateCreated</em>), usando a avaliação da posição anterior.
     *
     * @param cpBefore avaliação em centipeões antes do lance
     *                 (perspectiva do jogador que cometeu o erro)
     * @return classificação
     */
    public static Judgement classifyMateCreated(double cpBefore) {
        if (cpBefore < -999) return Judgement.INACCURACY;
        if (cpBefore < -700) return Judgement.MISTAKE;
        return Judgement.BLUNDER;
    }

    /**
     * Atalho: retorna {@code true} se o lance for blunder.
     *
     * @param cpBefore eval (brancas) antes do lance
     * @param cpAfter  eval (brancas) depois do lance
     * @param whiteToMove quem jogou
     */
    public static boolean isBlunder(double cpBefore, double cpAfter, boolean whiteToMove) {
        return classify(cpBefore, cpAfter, whiteToMove) == Judgement.BLUNDER;
    }

    // --- fórmula do Lichess -----------------------------------------------

    /**
     * Converte centipeões em probabilidade de vitória no intervalo [-1, +1].
     *
     * <pre>
     *   winChances(cp) = 2 / (1 + exp(-0.00368208 * cp)) - 1
     * </pre>
     *
     * A avaliação é limitada ao intervalo [-1000, +1000] antes do cálculo.
     *
     * @param cp centipeões na perspectiva das brancas
     * @return número no intervalo [-1, +1]
     */
    public static double winningChances(double cp) {
        double ceiled = Math.max(-CP_CEILING, Math.min(CP_CEILING, cp));
        double result = 2.0 / (1.0 + Math.exp(MULTIPLIER * ceiled)) - 1.0;
        return Math.max(-1.0, Math.min(1.0, result));
    }

    // --- helpers internos -------------------------------------------------

    private static Judgement fromDelta(double delta) {
        if (delta >= BLUNDER_THRESHOLD)    return Judgement.BLUNDER;
        if (delta >= MISTAKE_THRESHOLD)    return Judgement.MISTAKE;
        if (delta >= INACCURACY_THRESHOLD) return Judgement.INACCURACY;
        return Judgement.GOOD;
    }
}
