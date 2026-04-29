package com.chess.analyzer.service;

import com.chess.analyzer.entity.LanceEntity;
import com.chess.analyzer.entity.PartidaEntity;
import com.chess.analyzer.repository.LanceRepository;
import com.chess.analyzer.repository.PuzzleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Serviço que fornece ECOs e puzzles lidos <em>diretamente do banco de dados</em>.
 *
 * <p>Usa JPQL com {@code JOIN FETCH} para carregar os lances blunder junto com
 * suas partidas. A agregação por ECO é feita em Java para evitar incompatibilidades
 * com queries nativas {@code GROUP BY} no Hibernate 7 (Spring Boot 4.x).</p>
 */
@Service
@Transactional(readOnly = true)
public class PuzzleDbService {

    private static final Logger log = LoggerFactory.getLogger(PuzzleDbService.class);

    private final PuzzleRepository puzzleRepository;
    private final LanceRepository  lanceRepository;

    public PuzzleDbService(PuzzleRepository puzzleRepository,
                           LanceRepository  lanceRepository) {
        this.puzzleRepository = puzzleRepository;
        this.lanceRepository  = lanceRepository;
    }

    // ── ECOs disponíveis no banco ──────────────────────────────────────────

    /**
     * Lista os ECOs que possuem lances {@code blunder=true} no banco de dados.
     * Delega para {@link #listEcos(String)} sem filtro de jogador.
     */
    public List<Map<String, Object>> listEcos() {
        return listEcos(null);
    }

    /**
     * Lista os ECOs que possuem blunders, opcionalmente filtrando pelo jogador
     * que estava na vez e cometeu o erro.
     *
     * <p>Quando {@code playerFilter} é fornecido (não nulo/em branco), apenas
     * os lances em que o nome do jogador (case-insensitive, substring) corresponde
     * ao filtro são contabilizados. ECOs sem nenhum blunder do jogador filtrado
     * são omitidos do resultado.</p>
     *
     * @param playerFilter nome (ou parte do nome) do jogador a filtrar; {@code null} = sem filtro
     */
    public List<Map<String, Object>> listEcos(String playerFilter) {
        List<LanceEntity> blunders = puzzleRepository.findAllBlundersWithPartida();
        log.info("listEcos(player='{}') → {} lance(s) blunder no banco", playerFilter, blunders.size());

        boolean hasFilter  = playerFilter != null && !playerFilter.isBlank();
        String  lowerFilter = hasFilter ? playerFilter.strip().toLowerCase() : null;

        Map<String, EcoAgg> byEco = new TreeMap<>();

        for (LanceEntity lance : blunders) {
            PartidaEntity partida = lance.getPartida();
            String eco = partida.getEco();
            if (eco == null || eco.isBlank() || "?".equals(eco.trim())) continue;

            // ── Identifica quem estava na vez e cometeu o blunder ──────────
            String white        = nvl(partida.getWhite());
            String black        = nvl(partida.getBlack());
            String blunderMaker = lance.isVezBrancas() ? white : black;

            // Se há filtro, pula lances cujo jogador não corresponde
            if (hasFilter && !blunderMaker.toLowerCase().contains(lowerFilter)) continue;

            EcoAgg agg = byEco.computeIfAbsent(eco, k -> new EcoAgg(eco));
            agg.gameIds.add(partida.getId());
            agg.blunderCount++;

            if (agg.openingName == null && partida.getOpening() != null
                    && !partida.getOpening().isBlank()
                    && !"?".equals(partida.getOpening().trim())) {
                agg.openingName = partida.getOpening();
            }

            // Estatísticas por jogador (sempre acumuladas para uso futuro)
            if (!blunderMaker.isEmpty()) {
                PlayerAgg pa = agg.playerStats.computeIfAbsent(blunderMaker, k -> new PlayerAgg());
                pa.blundersMade++;
                pa.gameIds.add(partida.getId());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(byEco.size());
        for (EcoAgg agg : byEco.values()) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("eco",      agg.eco);
            dto.put("opening",  agg.openingName != null ? agg.openingName : "");
            dto.put("games",    (long) agg.gameIds.size());
            dto.put("blunders", agg.blunderCount);

            List<Map<String, Object>> playerList = new ArrayList<>();
            for (Map.Entry<String, PlayerAgg> entry : agg.playerStats.entrySet()) {
                Map<String, Object> ps = new LinkedHashMap<>();
                ps.put("name",     entry.getKey());
                ps.put("games",    (long) entry.getValue().gameIds.size());
                ps.put("blunders", entry.getValue().blundersMade);
                playerList.add(ps);
            }
            dto.put("playerStats", playerList);
            result.add(dto);
        }

        log.info("listEcos(player='{}') → {} ECO(s)", playerFilter, result.size());
        return result;
    }

    /**
     * Indica se o banco possui ao menos um lance blunder analisado.
     */
    public boolean hasData() {
        try {
            return !puzzleRepository.findAllBlundersWithPartida().isEmpty();
        } catch (Exception e) {
            log.warn("hasData() falhou: {}", e.getMessage());
            return false;
        }
    }

    // ── Puzzles por ECO ────────────────────────────────────────────────────

    /**
     * Gera a lista de puzzles para o ECO informado a partir dos lances
     * marcados como {@code blunder=true} no banco de dados.
     * Delega para {@link #getPuzzlesByEco(String, String)} sem filtro de jogador.
     *
     * @param eco código ECO (ex: "B20"), case-insensitive
     */
    public List<Map<String, Object>> getPuzzlesByEco(String eco) {
        return getPuzzlesByEco(eco, null);
    }

    /**
     * Gera a lista de puzzles para o ECO informado, filtrando opcionalmente pelo
     * jogador que estava na vez e cometeu o blunder.
     *
     * <p>Para cada lance blunder, a avaliação <em>antes</em> do blunder
     * ({@code evalBefore}) é obtida consultando o lance imediatamente anterior
     * na mesma partida. Se não houver lance anterior ou ele não estiver
     * analisado, usa {@code 0.0} como referência.</p>
     *
     * @param eco          código ECO (ex: "B20"), case-insensitive
     * @param playerFilter filtro pelo nome do jogador que cometeu o blunder
     *                     (case-insensitive, substring); {@code null} = sem filtro
     * @return lista de Maps compatível com o formato esperado pela UI de puzzles
     */
    public List<Map<String, Object>> getPuzzlesByEco(String eco, String playerFilter) {
        if (eco == null || eco.isBlank()) return List.of();

        List<LanceEntity> blunders = puzzleRepository.findBlundersByEco(eco.trim());
        log.info("getPuzzlesByEco({}, player='{}') → {} blunder(s) encontrado(s)",
                eco, playerFilter, blunders.size());

        boolean hasFilter   = playerFilter != null && !playerFilter.isBlank();
        String  lowerFilter = hasFilter ? playerFilter.strip().toLowerCase() : null;

        List<Map<String, Object>> puzzles = new ArrayList<>(blunders.size());
        int puzzleId = 0;

        for (LanceEntity lance : blunders) {
            PartidaEntity partida = lance.getPartida();

            // Aplica filtro por jogador: verifica quem estava na vez do blunder
            if (hasFilter) {
                String white        = nvl(partida.getWhite());
                String black        = nvl(partida.getBlack());
                String blunderMaker = lance.isVezBrancas() ? white : black;
                if (!blunderMaker.toLowerCase().contains(lowerFilter)) continue;
            }

            // Avaliação antes do blunder (perspectiva brancas)
            double evalBefore = resolveEvalBefore(partida.getId(), lance.getOrdem());

            // Avaliação depois do blunder
            Double  evalAfter = lance.getEval();
            Integer mateIn    = lance.getMateEm();
            double  curEval   = mateIn != null
                    ? (mateIn > 0 ? 100.0 : -100.0)
                    : (evalAfter != null ? evalAfter : 0.0);

            // Queda de avaliação do ponto de vista do jogador que cometeu o blunder
            double drop = lance.isVezBrancas()
                    ? evalBefore - curEval     // brancas: perde vantagem positiva
                    : curEval    - evalBefore; // pretas:  sobe o valor (perde vantagem negativa)

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id",          puzzleId++);
            dto.put("lanceId",     lance.getId());
            dto.put("fen",         lance.getFenAntes());
            dto.put("sideToMove",  fenSide(lance.getFenAntes()));
            dto.put("playedUci",   lance.getUci());
            dto.put("playedSan",   lance.getSan());
            dto.put("bestMove",    lance.getMelhorLance());
            dto.put("evalBefore",  round(evalBefore));
            dto.put("evalAfter",   mateIn != null ? null : (evalAfter != null ? round(evalAfter) : null));
            dto.put("mateInAfter", mateIn);
            dto.put("drop",        round(drop));
            dto.put("moveNumber",  lance.getNumeroLance());
            dto.put("white",       nvl(partida.getWhite()));
            dto.put("black",       nvl(partida.getBlack()));
            dto.put("eco",         nvl(partida.getEco()));
            dto.put("opening",     nvl(partida.getOpening()));
            dto.put("gameTitle",   partida.titulo());
            dto.put("gameId",      partida.getGameId());
            dto.put("date",        partida.getDate() != null ? partida.getDate().toString() : null);
            dto.put("whiteElo",    partida.getWhiteElo());
            dto.put("blackElo",    partida.getBlackElo());
            dto.put("site",        nvl(partida.getSite()));
            puzzles.add(dto);
        }

        return puzzles;
    }

    // ── Helpers privados ───────────────────────────────────────────────────

    /**
     * Recupera a avaliação do lance imediatamente anterior ao blunder
     * (perspectiva brancas). Retorna {@code 0.0} se não houver lance anterior
     * ou se ele não estiver analisado.
     */
    private double resolveEvalBefore(Long partidaId, int ordemBlunder) {
        if (ordemBlunder <= 1) return 0.0;
        Optional<LanceEntity> prev =
                lanceRepository.findByPartidaIdAndOrdem(partidaId, ordemBlunder - 1);
        if (prev.isEmpty() || !prev.get().isAnalisado()) return 0.0;
        LanceEntity p = prev.get();
        if (p.getMateEm() != null) return p.getMateEm() > 0 ? 100.0 : -100.0;
        return p.getEval() != null ? p.getEval() : 0.0;
    }

    /** Extrai o lado a jogar do FEN ("w" ou "b"). */
    private static String fenSide(String fen) {
        if (fen == null) return "w";
        String[] parts = fen.split("\\s+");
        return parts.length > 1 ? parts[1] : "w";
    }

    /** Arredonda para 2 casas decimais. */
    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Substitui nulos / vazios / "?" por string vazia. */
    private static String nvl(String v) {
        return (v == null || v.isBlank() || "?".equals(v.trim())) ? "" : v;
    }

    // ── Classes internas de agregação ─────────────────────────────────────────

    /** Estrutura interna para agregar dados de um ECO durante o processamento. */
    private static class EcoAgg {
        final String eco;
        String openingName;
        final Set<Long> gameIds = new LinkedHashSet<>();
        long blunderCount;
        /** Estatísticas por jogador: nome → {games, blunders cometidos} */
        final Map<String, PlayerAgg> playerStats = new LinkedHashMap<>();

        EcoAgg(String eco) { this.eco = eco; }
    }

    /** Agrega, por jogador, as partidas em que apareceu e os blunders que cometeu. */
    private static class PlayerAgg {
        /** IDs das partidas em que este jogador apareceu (como brancas ou pretas). */
        final Set<Long> gameIds = new LinkedHashSet<>();
        /** Quantidade de blunders cometidos pelo jogador neste ECO. */
        int blundersMade;
    }
}
