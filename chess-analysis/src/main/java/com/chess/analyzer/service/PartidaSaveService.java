package com.chess.analyzer.service;

import com.chess.analyzer.model.GameData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orquestra o salvamento em lote de partidas analisadas.
 *
 * <p>Cada partida e delegada para {@link PartidaUpsertService#upsertPartida},
 * que roda em sua propria transacao ({@code REQUIRES_NEW}). Assim, uma falha
 * em uma partida nao reverte as demais.</p>
 *
 * <p><strong>Por que dois servicos?</strong> O Spring AOP nao intercepta
 * chamadas self-invocation ({@code this.metodo()}), logo um
 * {@code @Transactional} declarado no proprio {@code PartidaSaveService}
 * seria silenciosamente ignorado. Separar o metodo transacional em um bean
 * proprio garante que a chamada passe pelo proxy CGLIB.</p>
 */
@Service
public class PartidaSaveService {

    private static final Logger log = LoggerFactory.getLogger(PartidaSaveService.class);

    private final PartidaUpsertService upsertService;

    public PartidaSaveService(PartidaUpsertService upsertService) {
        this.upsertService = upsertService;
    }

    // ── DTOs de resultado ────────────────────────────────────────────────────

    public record SaveResult(int index, String title, String status) {
        public static SaveResult criada(int idx, String title) {
            return new SaveResult(idx, title, "CRIADA");
        }
        public static SaveResult atualizada(int idx, String title) {
            return new SaveResult(idx, title, "ATUALIZADA");
        }
        public static SaveResult ignorada(int idx, String title, String motivo) {
            return new SaveResult(idx, title, "IGNORADA: " + motivo);
        }
    }

    // ── Processamento em lote ────────────────────────────────────────────────

    /**
     * Salva uma lista de partidas analisadas.
     *
     * @param games        lista de GameData (com ou sem analise Stockfish)
     * @param onlyAnalyzed se {@code true}, ignora partidas sem analise completa
     * @return lista de resultados por partida
     */
    public List<SaveResult> salvarPartidasAnalisadas(List<GameData> games, boolean onlyAnalyzed) {
        List<SaveResult> results = new ArrayList<>();

        for (GameData game : games) {
            if (onlyAnalyzed && !game.isFullyAnalyzed()) {
                results.add(SaveResult.ignorada(game.getIndex(), game.getTitle(),
                        "partida nao esta completamente analisada"));
                continue;
            }

            try {
                // Chama atraves do bean injetado — o proxy CGLIB intercepta
                // corretamente e aplica @Transactional(REQUIRES_NEW)
                SaveResult r = upsertService.upsertPartida(game);
                results.add(r);
                log.info("Partida [{}] '{}' -> {}", game.getIndex(), game.getTitle(), r.status());
            } catch (Exception e) {
                log.error("Erro ao salvar partida [{}]: {}", game.getIndex(), e.getMessage(), e);
                results.add(SaveResult.ignorada(game.getIndex(), game.getTitle(),
                        "erro: " + e.getMessage()));
            }
        }

        return results;
    }
}
