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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bean isolado responsável pelo upsert transacional de uma única partida.
 *
 * <p>Existe como serviço separado de {@link PartidaSaveService} para contornar
 * a limitação do Spring AOP: chamadas self-invocation (this.metodo()) dentro
 * da mesma classe não passam pelo proxy CGLIB e, portanto, ignoram qualquer
 * anotação {@code @Transactional}. Ao extrair o método para um bean próprio,
 * a chamada atravessa o proxy e a propagação REQUIRES_NEW é honrada.</p>
 */
@Service
public class PartidaUpsertService {

    private static final Logger log = LoggerFactory.getLogger(PartidaUpsertService.class);

    private final PartidaRepository partidaRepository;
    private final LanceRepository   lanceRepository;

    public PartidaUpsertService(PartidaRepository partidaRepository,
                                LanceRepository lanceRepository) {
        this.partidaRepository = partidaRepository;
        this.lanceRepository   = lanceRepository;
    }

    /**
     * Insere ou atualiza uma única partida em sua própria transação.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PartidaSaveService.SaveResult upsertPartida(GameData game) {
        String gameId = game.getGameId();
        Optional<PartidaEntity> existente = partidaRepository.findByGameId(gameId);

        if (existente.isPresent()) {
            PartidaEntity entidade = existente.get();
            lanceRepository.deleteByPartidaId(entidade.getId());
            lanceRepository.flush();
            adicionarLances(entidade, game);
            partidaRepository.save(entidade);
            log.debug("Partida '{}' ({}) atualizada.", game.getTitle(), gameId);
            return PartidaSaveService.SaveResult.atualizada(game.getIndex(), game.getTitle());
        } else {
            Map<String, String> tags = game.getTags();
            String fontePgn = tags.getOrDefault("__fonte_pgn__", "unknown");
            PartidaEntity entidade = new PartidaEntity(
                    game.getIndex(), fontePgn, gameId, tags, game.getInitialFen());
            adicionarLances(entidade, game);
            partidaRepository.save(entidade);
            log.debug("Partida '{}' ({}) criada.", game.getTitle(), gameId);
            return PartidaSaveService.SaveResult.criada(game.getIndex(), game.getTitle());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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
                // Classifica blunder pelo critério Lichess (winning chances)
                boolean blunder = GameAnalysisService.isBlunder(m);
                lance.registrarAnalise(
                        m.getEval(),
                        m.getMateIn(),
                        m.getBestMove(),
                        m.getPv(),
                        blunder
                );
            }
            entidade.addLance(lance);
        }
    }
}
