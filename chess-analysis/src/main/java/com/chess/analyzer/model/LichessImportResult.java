package com.chess.analyzer.model;

import com.chess.analyzer.service.PartidaSaveService;

import java.util.List;

/**
 * DTO com o resumo de uma importação de partidas do Lichess.
 *
 * @param username    login do usuário no Lichess
 * @param total       total de partidas processadas
 * @param criadas     novas partidas inseridas no banco
 * @param atualizadas partidas já existentes que foram atualizadas
 * @param ignoradas   partidas ignoradas por erro ou ausência de lances
 * @param detalhes    resultado individual de cada partida (persistência)
 * @param games       summaries das partidas carregadas em memória (para a UI)
 */
public record LichessImportResult(
        String username,
        int    total,
        long   criadas,
        long   atualizadas,
        long   ignoradas,
        List<PartidaSaveService.SaveResult> detalhes,
        List<GameData.Summary>              games
) {

    /** Resultado de importação vazia (nenhuma partida encontrada). */
    public static LichessImportResult vazio(String username) {
        return new LichessImportResult(username, 0, 0L, 0L, 0L, List.of(), List.of());
    }
}
