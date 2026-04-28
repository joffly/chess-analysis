package com.chess.analyzer.service;

import com.chess.analyzer.entity.LanceEntity;
import com.chess.analyzer.entity.PartidaEntity;
import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;
import com.chess.analyzer.repository.LanceRepository;
import com.chess.analyzer.repository.PartidaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PartidaSaveService {

    private static final Logger log = LoggerFactory.getLogger(PartidaSaveService.class);

    private final PartidaRepository partidaRepository;
    private final LanceRepository lanceRepository;

    public PartidaSaveService(PartidaRepository partidaRepository,
                               LanceRepository lanceRepository) {
        this.partidaRepository = partidaRepository;
        this.lanceRepository   = lanceRepository;
    }

    /**
     * Resultado do processamento de uma partida individual.
     */
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

    /**
     * Salva uma lista de partidas analisadas.
     *
     * <p>Não há transação envolvendo o loop inteiro: cada partida é commitada
     * individualmente por {@link #upsertPartida(GameData)}, de modo que uma
     * falha numa partida não reverte as que já foram salvas.</p>
     *
     * @param games        lista de GameData com análise
     * @param onlyAnalyzed se true, ignora partidas sem análise completa
     * @return lista de resultados por partida
     */
    public List<SaveResult> salvarPartidasAnalisadas(List<GameData> games, boolean onlyAnalyzed) {
        List<SaveResult> results = new ArrayList<>();

        for (GameData game : games) {
            if (onlyAnalyzed && !game.isFullyAnalyzed()) {
                results.add(SaveResult.ignorada(game.getIndex(), game.getTitle(),
                        "partida não está completamente analisada"));
                continue;
            }

            try {
                SaveResult r = upsertPartida(game);
                results.add(r);
                log.info("Partida [{}] '{}' → {}", game.getIndex(), game.getTitle(), r.status());
            } catch (Exception e) {
                log.error("Erro ao salvar partida [{}]: {}", game.getIndex(), e.getMessage(), e);
                results.add(SaveResult.ignorada(game.getIndex(), game.getTitle(),
                        "erro: " + e.getMessage()));
            }
        }

        return results;
    }

    // ── Upsert — cada chamada roda em sua própria transação ──────────────────

    /**
     * Insere ou atualiza uma única partida em uma transação própria.
     *
     * <p>{@code REQUIRES_NEW} suspende qualquer transação externa (caso este
     * método seja chamado de dentro de outra transação) e garante um commit
     * imediato ao término, independentemente do restante do loop.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SaveResult upsertPartida(GameData game) {
        String gameId = game.getGameId();
        Optional<PartidaEntity> existente = partidaRepository.findByGameId(gameId);

        if (existente.isPresent()) {
            // UPDATE: apaga lances antigos e regrava com avaliações novas
            PartidaEntity entidade = existente.get();
            lanceRepository.deleteByPartidaId(entidade.getId());
            lanceRepository.flush();
            adicionarLances(entidade, game);
            partidaRepository.save(entidade);
            return SaveResult.atualizada(game.getIndex(), game.getTitle());
        } else {
            // INSERT: cria entidade nova
            Map<String, String> tags = game.getTags();
            String fontePgn = tags.getOrDefault("__fonte_pgn__", "unknown");
            PartidaEntity entidade = new PartidaEntity(
                    game.getIndex(), fontePgn, gameId, tags, game.getInitialFen());
            adicionarLances(entidade, game);
            partidaRepository.save(entidade);
            return SaveResult.criada(game.getIndex(), game.getTitle());
        }
    }

    private void adicionarLances(PartidaEntity entidade, GameData game) {
        List<MoveEntry> moves = game.getMoves();
        for (int i = 0; i < moves.size(); i++) {
            MoveEntry m = moves.get(i);
            LanceEntity lance = new LanceEntity(
                    i + 1,
                    m.getMoveNumber(),
                    m.isWhiteTurn(),
                    m.getUci(),
                    m.getSan(),
                    m.getFenBefore(),
                    m.getFenAfter()
            );
            if (m.isAnalyzed()) {
                lance.registrarAnalise(
                        m.getEval(),
                        m.getMateIn(),
                        m.getBestMove(),
                        m.getPv(),
                        false
                );
            }
            entidade.addLance(lance);
        }
    }
}
