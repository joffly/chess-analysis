package com.chess.analyzer.model;

/**
 * Projeção Spring Data para o resultado da query nativa que agrega
 * ECOs com lances blunder no banco de dados.
 *
 * <p>Os nomes dos getters devem corresponder (case-insensitive) aos
 * aliases definidos na query SQL nativa de {@code PuzzleRepository}.</p>
 */
public interface EcoBlunderSummary {

    /** Código ECO (ex: "B20"). */
    String getEco();

    /** Nome da abertura (pode ser {@code null} se não cadastrado na partida). */
    String getOpening();

    /** Quantidade de partidas distintas que possuem ao menos um blunder neste ECO. */
    Long getGames();

    /** Total de lances blunder neste ECO. */
    Long getBlunders();
}
