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
     * Lista os ECOs que possuem lances {@code blunder=true} e {@code analisado=true}
     * no banco de dados, ordenados alfabeticamente pelo código ECO.
     *
     * <p>Carrega todos os lances blunder via JPQL e agrega por ECO em Java.
     * Cada ECO retorna: {@code eco}, {@code opening}, {@code games}, {@code blunders}.</p>
     */
    public List<Map<String, Object>> listEcos() {
        List<LanceEntity> blunders = puzzleRepository.findAllBlundersWithPartida();
        log.info("listEcos() → {} lance(s) blunder encontrado(s) no banco", blunders.size());

        // Agrega por ECO em Java
        Map<String, EcoAgg> byEco = new TreeMap<>();

        for (LanceEntity lance : blunders) {
            PartidaEntity partida = lance.getPartida();
            String eco = partida.getEco();

            // Filtra ECOs nulos/vazios/'?'
            if (eco == null || eco.isBlank() || "?".equals(eco.trim())) continue;

            EcoAgg agg = byEco.computeIfAbsent(eco, k -> new EcoAgg(eco));
            agg.gameIds.add(partida.getId());
            agg.blunderCount++;

            // Guarda o primeiro nome de abertura não-vazio encontrado
            if (agg.openingName == null && partida.getOpening() != null
                    && !partida.getOpening().isBlank()
                    && !"?".equals(partida.getOpening().trim())) {
                agg.openingName = partida.getOpening();
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(byEco.size());
        for (EcoAgg agg : byEco.values()) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("eco",      agg.eco);
            dto.put("opening",  agg.openingName != null ? agg.openingName : "");
            dto.put("games",    (long) agg.gameIds.size());
            dto.put("blunders", agg.blunderCount);
            result.add(dto);
        }

        log.info("listEcos() → {} ECO(s) com blunders", result.size());
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
     *
     * <p>Para cada lance blunder, a avaliação <em>antes</em> do blunder
     * ({@code evalBefore}) é obtida consultando o lance imediatamente anterior
     * na mesma partida. Se não houver lance anterior ou ele não estiver
     * analisado, usa {@code 0.0} como referência.</p>
     *
     * @param eco código ECO (ex: "B20"), case-insensitive
     * @return lista de Maps compatível com o formato esperado pela UI de puzzles
     */
    public List<Map<String, Object>> getPuzzlesByEco(String eco) {
        if (eco == null || eco.isBlank()) return List.of();

        List<LanceEntity> blunders = puzzleRepository.findBlundersByEco(eco.trim());
        log.info("getPuzzlesByEco({}) → {} blunder(s) encontrado(s)", eco, blunders.size());

        List<Map<String, Object>> puzzles = new ArrayList<>(blunders.size());
        int puzzleId = 0;

        for (LanceEntity lance : blunders) {
            PartidaEntity partida = lance.getPartida();

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

    /** Estrutura interna para agregar dados de um ECO durante o processamento. */
    private static class EcoAgg {
        final String eco;
        String openingName;
        final Set<Long> gameIds = new LinkedHashSet<>();
        long blunderCount;

        EcoAgg(String eco) {
            this.eco = eco;
        }
    }
}
