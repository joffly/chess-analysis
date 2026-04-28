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
 * Bean isolado responsavel pelo upsert transacional de uma unica partida.
 *
 * <p>Existe como servico separado de {@link PartidaSaveService} para contornar
 * a limitacao do Spring AOP: chamadas self-invocation (this.metodo()) dentro
 * da mesma classe nao passam pelo proxy CGLIB e, portanto, ignoram qualquer
 * anotacao {@code @Transactional}. Ao extrair o metodo para um bean proprio,
 * a chamada atravessa o proxy e a propagacao REQUIRES_NEW e honrada.</p>
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
     * Insere ou atualiza uma unica partida em sua propria transacao.
     *
     * <p>REQUIRES_NEW garante commit imediato ao termino do metodo,
     * independentemente de qualquer transacao externa. Uma falha nesta
     * partida nao afeta as demais do lote.</p>
     *
     * @param game GameData ja analisado (ou nao) pronto para persistencia
     * @return resultado da operacao (CRIADA / ATUALIZADA)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PartidaSaveService.SaveResult upsertPartida(GameData game) {
        String gameId = game.getGameId();
        Optional<PartidaEntity> existente = partidaRepository.findByGameId(gameId);

        if (existente.isPresent()) {
            // UPDATE: apaga lances antigos e regrava com avaliacoes novas
            PartidaEntity entidade = existente.get();
            lanceRepository.deleteByPartidaId(entidade.getId());
            lanceRepository.flush();
            adicionarLances(entidade, game);
            partidaRepository.save(entidade);
            log.debug("Partida '{}' ({}) atualizada.", game.getTitle(), gameId);
            return PartidaSaveService.SaveResult.atualizada(game.getIndex(), game.getTitle());
        } else {
            // INSERT: cria entidade nova
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
