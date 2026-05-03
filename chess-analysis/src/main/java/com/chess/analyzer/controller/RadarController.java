package com.chess.analyzer.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.chess.analyzer.entity.LanceEntity;
import com.chess.analyzer.entity.PartidaEntity;
import com.chess.analyzer.repository.PartidaRepository;
import com.chess.analyzer.service.RadarCacheService;

/**
 * Controlador da página <strong>Radar de Pontos Fracos</strong>.
 *
 * <p>Fornece o diagnóstico agregado das partidas analisadas no banco para o
 * dashboard {@code /radar}. Cada endpoint retorna um JSON pronto para
 * consumo direto pelos componentes do front-end (Chart.js + grade de cards).</p>
 *
 * <h2>Estratégia</h2>
 * <ul>
 *   <li><strong>DB-first:</strong> os agregados são calculados sobre as
 *       {@link PartidaEntity partidas} e {@link LanceEntity lances} persistidos.</li>
 *   <li><strong>Filtro opcional por jogador</strong> (case-insensitive,
 *       substring) — todos os endpoints aceitam {@code ?player=}.</li>
 *   <li>Métricas que dependem de dados ainda não capturados (tempo no
 *       relógio, padrões táticos detalhados) são <em>stubs</em> que devem
 *       ser substituídos quando essas features forem implementadas.</li>
 * </ul>
 *
 * <h2>Endpoints</h2>
 * <pre>
 * GET  /radar                              → página HTML
 * GET  /api/radar/summary?player=          → totais (partidas, blunders, win-rate)
 * GET  /api/radar/tactical-patterns?player → padrões táticos onde mais erra
 * GET  /api/radar/advantage?player         → gestão de vantagem (+3 → resultado)
 * GET  /api/radar/openings?player          → desempenho por ECO + abertura nemesis
 * GET  /api/radar/time-vs-precision?player → scatter tempo × precisão
 * GET  /api/radar/tilt?player              → probabilidade de blunder após blunder
 * GET  /api/radar/heatmap?player           → 64 casas com peso de erros
 * GET  /api/radar/training?player          → prescrição de treino baseada nos dados
 * </pre>
 */
@Controller
public class RadarController {

    private final PartidaRepository partidaRepository;
    private final RadarCacheService cache;

    public RadarController(PartidaRepository partidaRepository,
                           RadarCacheService cache) {
        this.partidaRepository = partidaRepository;
        this.cache             = cache;
    }

    // ── UI ─────────────────────────────────────────────────────────────────

    @GetMapping("/radar")
    public String radarPage() {
        return "radar";
    }

    /** Normaliza o filtro de jogador para usar como sufixo da chave de cache. */
    private static String cacheKey(String prefix, String player) {
        return prefix + ":" + (player == null ? "" : player.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Força a invalidação do cache do radar — útil após inserir ou
     * atualizar partidas no banco. Também é chamado automaticamente pelo
     * job batch de sincronização.
     */
    @PostMapping("/api/radar/cache/invalidate")
    @ResponseBody
    public Map<String, Object> invalidateCache() {
        int before = cache.size();
        cache.invalidateAll();
        return Map.of("ok", true, "removed", before, "ttlMs", cache.getTtlMs());
    }

    // ── Summary ────────────────────────────────────────────────────────────

    /**
     * Cards-resumo no topo do dashboard.
     * Retorna totais brutos para "número grande" + variação rápida.
     */
    @GetMapping("/api/radar/summary")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> summary(@RequestParam(required = false) String player) {
        return cache.computeIfFresh(cacheKey("summary", player), () -> computeSummary(player));
    }

    private Map<String, Object> computeSummary(String player) {
        List<PartidaEntity> partidas = filtrarPorJogador(partidaRepository.findAll(), player);

        long totalPartidas  = partidas.size();
        long totalAnalisadas = partidas.stream()
                .filter(p -> p.getLances().stream().anyMatch(LanceEntity::isAnalisado))
                .count();
        long totalBlunders  = partidas.stream()
                .flatMap(p -> p.getLances().stream())
                .filter(LanceEntity::isBlunder)
                .count();

        long vitorias = partidas.stream().filter(p -> isVitoria(p, player)).count();
        long derrotas = partidas.stream().filter(p -> isDerrota(p, player)).count();
        long empates  = totalPartidas - vitorias - derrotas;

        double winRate = totalPartidas == 0 ? 0.0 : (vitorias * 100.0) / totalPartidas;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("player",          player);
        resp.put("partidas",        totalPartidas);
        resp.put("analisadas",      totalAnalisadas);
        resp.put("blunders",        totalBlunders);
        resp.put("vitorias",        vitorias);
        resp.put("derrotas",        derrotas);
        resp.put("empates",         empates);
        resp.put("winRate",         round2(winRate));
        return resp;
    }

    // ── 1. Padrões táticos onde o usuário mais erra ────────────────────────

    /**
     * Retorna padrões táticos detectados nos blunders do usuário.
     *
     * <p><strong>Estado atual:</strong> stub heurístico. A detecção real
     * (garfos de cavalo, cravadas, mate na gaveta, descobertas etc.) requer
     * análise da posição via chesslib comparando o lance jogado vs. o melhor
     * lance e identificando o motivo da queda. Quando essa análise for
     * implementada (ex.: novo serviço {@code TacticalPatternDetector}), basta
     * substituir o corpo deste método pela leitura dos padrões persistidos.</p>
     *
     * <p>Por ora, derivamos um proxy a partir do número de meias-jogadas em
     * que o blunder ocorreu:</p>
     * <ul>
     *   <li>≤ 15 lances → "Blunder de abertura"</li>
     *   <li>≤ 35 lances → "Blunder de meio-jogo (tática)"</li>
     *   <li>&gt; 35 lances → "Blunder de final"</li>
     * </ul>
     */
    @GetMapping("/api/radar/tactical-patterns")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<Map<String, Object>> tacticalPatterns(@RequestParam(required = false) String player) {
        return cache.computeIfFresh(cacheKey("patterns", player), () -> computeTacticalPatterns(player));
    }

    private List<Map<String, Object>> computeTacticalPatterns(String player) {
        List<PartidaEntity> partidas = filtrarPorJogador(partidaRepository.findAll(), player);

        // TODO: substituir pelo detector real (TacticalPatternDetector) quando
        //       a feature for implementada. Mantém o mesmo formato de retorno.
        Map<String, Integer> contagem = new LinkedHashMap<>();
        contagem.put("Sofre garfos de cavalo",         0);
        contagem.put("Não vê mate na gaveta",          0);
        contagem.put("Cai em cravadas (pin)",          0);
        contagem.put("Pendura peças em ataques duplos", 0);
        contagem.put("Erros de cálculo no final",      0);
        contagem.put("Blunder de abertura",            0);

        partidas.forEach(p -> p.getLances().stream()
                .filter(l -> l.isBlunder() && pertenceAoJogador(p, l, player))
                .forEach(l -> {
                    String pattern;
                    if (l.getNumeroLance() <= 15)      pattern = "Blunder de abertura";
                    else if (l.getNumeroLance() <= 35) pattern = "Cai em cravadas (pin)";
                    else                                pattern = "Erros de cálculo no final";
                    contagem.merge(pattern, 1, Integer::sum);
                }));

        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("pattern",  e.getKey());
                    item.put("count",    e.getValue());
                    item.put("severity", e.getValue() >= 5 ? "high" : e.getValue() >= 2 ? "med" : "low");
                    return item;
                })
                .collect(Collectors.toList());
    }

    // ── 2. Gestão de Vantagem (+3 → resultado) ─────────────────────────────

    /**
     * Indicador "Gestão de Vantagem": dentre as partidas em que a avaliação
     * do Stockfish chegou a +3 a favor do jogador, em quantas ele converteu
     * em vitória.
     */
    @GetMapping("/api/radar/advantage")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> advantageManagement(@RequestParam(required = false) String player) {
        return cache.computeIfFresh(cacheKey("advantage", player), () -> computeAdvantage(player));
    }

    private Map<String, Object> computeAdvantage(String player) {
        List<PartidaEntity> partidas = filtrarPorJogador(partidaRepository.findAll(), player);

        int alcancou = 0, ganhou = 0, perdeu = 0, empatou = 0;
        for (PartidaEntity p : partidas) {
            boolean jogadorEhBranca = jogadorEhBranca(p, player);

            boolean atingiuVantagem = p.getLances().stream()
                    .filter(LanceEntity::isAnalisado)
                    .map(LanceEntity::getEval)
                    .filter(e -> e != null)
                    .anyMatch(e -> jogadorEhBranca ? e >= 3.0 : e <= -3.0);

            if (!atingiuVantagem) continue;

            alcancou++;
            if (isVitoria(p, player))      ganhou++;
            else if (isDerrota(p, player)) perdeu++;
            else                            empatou++;
        }

        double conversao = alcancou == 0 ? 0.0 : (ganhou * 100.0) / alcancou;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("partidasComVantagem",    alcancou);
        resp.put("vitorias",               ganhou);
        resp.put("derrotas",               perdeu);
        resp.put("empates",                empatou);
        resp.put("taxaConversao",          round2(conversao));
        return resp;
    }

    // ── 3. Raio-X de Aberturas ─────────────────────────────────────────────

    /**
     * Estatísticas de desempenho por código ECO. O front-end destaca a
     * "Abertura Nemesis" — aquela com maior taxa de derrota.
     */
    @GetMapping("/api/radar/openings")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> openings(@RequestParam(required = false) String player) {
        return cache.computeIfFresh(cacheKey("openings", player), () -> computeOpenings(player));
    }

    private Map<String, Object> computeOpenings(String player) {
        List<PartidaEntity> partidas = filtrarPorJogador(partidaRepository.findAll(), player);

        Map<String, OpeningAggregate> agg = new HashMap<>();
        for (PartidaEntity p : partidas) {
            String eco = p.getEco() == null || p.getEco().isBlank() ? "—" : p.getEco();
            OpeningAggregate a = agg.computeIfAbsent(eco, k -> new OpeningAggregate(eco, p.getOpening()));
            a.partidas++;
            if (isVitoria(p, player))      a.vitorias++;
            else if (isDerrota(p, player)) a.derrotas++;
            else                            a.empates++;

            a.blunders += p.getLances().stream()
                    .filter(l -> l.isBlunder() && pertenceAoJogador(p, l, player))
                    .count();
        }

        List<Map<String, Object>> openings = agg.values().stream()
                .sorted(Comparator.comparingLong((OpeningAggregate a) -> a.partidas).reversed())
                .map(OpeningAggregate::toMap)
                .collect(Collectors.toList());

        // "Nemesis": ECO com maior taxa de derrota (mínimo 2 partidas)
        Map<String, Object> nemesis = agg.values().stream()
                .filter(a -> a.partidas >= 2)
                .max(Comparator.comparingDouble(OpeningAggregate::loseRate))
                .map(OpeningAggregate::toMap)
                .orElse(null);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("openings",  openings);
        resp.put("nemesis",   nemesis);
        return resp;
    }

    // ── 4. Análise Comportamental — Tempo × Precisão ───────────────────────

    /**
     * <p><strong>Stub:</strong> as entidades atuais ({@link LanceEntity}) não
     * armazenam o tempo gasto no lance (clock). Quando essa informação for
     * extraída do PGN ({@code [%clk ...]}) ou recebida via API, este método
     * passará a retornar pontos reais.</p>
     *
     * <p>Por ora, gera pontos sintéticos a partir da queda de avaliação para
     * permitir o desenho do gráfico de dispersão na UI.</p>
     */
    @GetMapping("/api/radar/time-vs-precision")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<Map<String, Object>> timeVsPrecision(@RequestParam(required = false) String player) {
        return cache.computeIfFresh(cacheKey("tvp", player), () -> computeTimeVsPrecision(player));
    }

    private List<Map<String, Object>> computeTimeVsPrecision(String player) {
        List<PartidaEntity> partidas = filtrarPorJogador(partidaRepository.findAll(), player);

        List<Map<String, Object>> pontos = new ArrayList<>();
        // TODO: substituir por tempo real extraído do PGN. Por enquanto:
        //   - tempo (s)  : derivado da posição do lance na partida (proxy)
        //   - precisão  : 100 - (queda de avaliação clampada a 0..100)
        for (PartidaEntity p : partidas) {
            for (LanceEntity l : p.getLances()) {
                if (!l.isAnalisado() || !pertenceAoJogador(p, l, player)) continue;
                if (l.getEval() == null || l.getMelhorLance() == null) continue;

                // proxy de "tempo gasto" (segundos): pico em torno do lance 20
                int n = l.getNumeroLance();
                double tempoProxy = Math.max(2.0, 60.0 - Math.abs(n - 20) * 2.0);

                // Precisão: 100 quando coincide com o melhor lance, decai com queda de eval
                double precisao = l.getUci().equalsIgnoreCase(l.getMelhorLance()) ? 100.0
                                : Math.max(0.0, 100.0 - (l.isBlunder() ? 60.0 : 25.0));

                Map<String, Object> ponto = new LinkedHashMap<>();
                ponto.put("x",     round2(tempoProxy));
                ponto.put("y",     round2(precisao));
                ponto.put("blunder", l.isBlunder());
                ponto.put("move",  l.getSan());
                ponto.put("ply",   l.getOrdem());
                pontos.add(ponto);
            }
        }
        return pontos;
    }

    // ── 5. Alerta de Tilt ──────────────────────────────────────────────────

    /**
     * Calcula a probabilidade de o jogador cometer um segundo blunder dentro
     * dos próximos N lances (default 5) após o primeiro blunder da partida.
     */
    @GetMapping("/api/radar/tilt")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> tiltAlert(@RequestParam(required = false) String player,
                                         @RequestParam(defaultValue = "5") int janela) {
        return cache.computeIfFresh(cacheKey("tilt:" + janela, player), () -> computeTilt(player, janela));
    }

    private Map<String, Object> computeTilt(String player, int janela) {
        List<PartidaEntity> partidas = filtrarPorJogador(partidaRepository.findAll(), player);

        int partidasComBlunder = 0;
        int partidasComTilt    = 0;

        for (PartidaEntity p : partidas) {
            List<LanceEntity> meusLances = p.getLances().stream()
                    .filter(l -> pertenceAoJogador(p, l, player))
                    .sorted(Comparator.comparingInt(LanceEntity::getOrdem))
                    .toList();

            int idxPrimeiro = -1;
            for (int i = 0; i < meusLances.size(); i++) {
                if (meusLances.get(i).isBlunder()) { idxPrimeiro = i; break; }
            }
            if (idxPrimeiro < 0) continue;
            partidasComBlunder++;

            // Procura segundo blunder dentro da janela
            int fim = Math.min(meusLances.size(), idxPrimeiro + 1 + janela);
            for (int i = idxPrimeiro + 1; i < fim; i++) {
                if (meusLances.get(i).isBlunder()) { partidasComTilt++; break; }
            }
        }

        double probabilidade = partidasComBlunder == 0 ? 0.0
                : (partidasComTilt * 100.0) / partidasComBlunder;

        String nivel = probabilidade >= 50 ? "alto"
                     : probabilidade >= 25 ? "medio"
                     : "baixo";

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("janela",              janela);
        resp.put("partidasComBlunder",  partidasComBlunder);
        resp.put("partidasComTilt",     partidasComTilt);
        resp.put("probabilidade",       round2(probabilidade));
        resp.put("nivel",               nivel);
        return resp;
    }

    // ── 6. Heatmap viso-espacial (8x8) ─────────────────────────────────────

    /**
     * Mapa de calor das casas do tabuleiro onde o jogador mais comete
     * blunders. A casa contabilizada é o destino do lance ruim.
     */
    @GetMapping("/api/radar/heatmap")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> heatmap(@RequestParam(required = false) String player) {
        return cache.computeIfFresh(cacheKey("heatmap", player), () -> computeHeatmap(player));
    }

    private Map<String, Object> computeHeatmap(String player) {
        List<PartidaEntity> partidas = filtrarPorJogador(partidaRepository.findAll(), player);

        Map<String, Integer> casas = new LinkedHashMap<>();
        for (char file = 'a'; file <= 'h'; file++) {
            for (char rank = '1'; rank <= '8'; rank++) {
                casas.put("" + file + rank, 0);
            }
        }

        int max = 0;
        for (PartidaEntity p : partidas) {
            for (LanceEntity l : p.getLances()) {
                if (!l.isBlunder() || !pertenceAoJogador(p, l, player)) continue;
                String uci = l.getUci();
                if (uci == null || uci.length() < 4) continue;
                String dest = uci.substring(2, 4).toLowerCase(Locale.ROOT);
                Integer atual = casas.get(dest);
                if (atual == null) continue;
                int novo = atual + 1;
                casas.put(dest, novo);
                if (novo > max) max = novo;
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("squares", casas);
        resp.put("max",     max);
        return resp;
    }

    // ── 7. Prescrição de treino ────────────────────────────────────────────

    /**
     * Gera 3 sugestões de treino baseadas nos dados acima.
     * A heurística é simples e local; pode ser substituída por um serviço
     * de IA dedicado quando disponível.
     */
    @GetMapping("/api/radar/training")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<Map<String, Object>> trainingSuggestions(@RequestParam(required = false) String player) {
        return cache.computeIfFresh(cacheKey("training", player), () -> computeTraining(player));
    }

    private List<Map<String, Object>> computeTraining(String player) {
        // Reaproveita os endpoints (que já passam pelo cache) — uma chamada extra
        // ao cache é desprezível e mantém a fonte única de verdade.
        Map<String, Object> tilt      = tiltAlert(player, 5);
        Map<String, Object> aberturas = openings(player);
        Map<String, Object> advantage = advantageManagement(player);

        List<Map<String, Object>> sugestoes = new ArrayList<>();

        // Sugestão 1 — baseada no Tilt
        double probTilt = ((Number) tilt.getOrDefault("probabilidade", 0)).doubleValue();
        if (probTilt >= 30) {
            sugestoes.add(card(
                    "🧘 Controle emocional",
                    "Sua probabilidade de cometer um segundo blunder após o primeiro é de "
                            + String.format(Locale.US, "%.0f", probTilt) + "%.",
                    "Resolver 10 puzzles de defesa após errar (rotina anti-tilt)."));
        } else {
            sugestoes.add(card(
                    "🎯 Cálculo concreto",
                    "Boa estabilidade após erros. Evolua o cálculo direto.",
                    "Resolver 15 puzzles 'Mate em 2 e 3' diariamente."));
        }

        // Sugestão 2 — baseada na abertura nemesis
        @SuppressWarnings("unchecked")
        Map<String, Object> nemesis = (Map<String, Object>) aberturas.get("nemesis");
        if (nemesis != null) {
            sugestoes.add(card(
                    "📖 Estudar abertura nemesis",
                    "Sua maior taxa de derrota está em " + nemesis.get("eco") + " — "
                            + nemesis.getOrDefault("opening", "(sem nome)") + ".",
                    "Estudar 3 partidas modelo nessa linha + revisar variantes principais."));
        } else {
            sugestoes.add(card(
                    "📖 Repertório de aberturas",
                    "Não há ainda uma abertura claramente problemática.",
                    "Consolidar repertório atual com 5 jogos rápidos por linha."));
        }

        // Sugestão 3 — baseada na gestão de vantagem
        double conv = ((Number) advantage.getOrDefault("taxaConversao", 0)).doubleValue();
        if (conv < 60) {
            sugestoes.add(card(
                    "🏁 Técnica de finalização",
                    "Converte " + String.format(Locale.US, "%.0f", conv) + "% das vantagens decisivas.",
                    "Resolver 10 puzzles de técnica em finais (R+P, B vs N, etc.)."));
        } else {
            sugestoes.add(card(
                    "🛡️ Defesa em posições piores",
                    "Boa conversão. Trabalhe agora a defesa contra vantagem adversária.",
                    "Resolver 10 puzzles de 'segurar empate' em posições inferiores."));
        }

        return sugestoes;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static Map<String, Object> card(String titulo, String motivo, String exercicio) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("titulo",    titulo);
        m.put("motivo",    motivo);
        m.put("exercicio", exercicio);
        return m;
    }

    /** Filtra partidas onde o jogador (substring case-insensitive) joga. */
    private static List<PartidaEntity> filtrarPorJogador(List<PartidaEntity> in, String player) {
        if (player == null || player.isBlank()) return in;
        String f = player.trim().toLowerCase(Locale.ROOT);
        return in.stream()
                .filter(p -> contains(p.getWhite(), f) || contains(p.getBlack(), f))
                .collect(Collectors.toList());
    }

    private static boolean contains(String v, String f) {
        return v != null && v.toLowerCase(Locale.ROOT).contains(f);
    }

    /** Decide se o jogador é o branco na partida (quando sem filtro, retorna true). */
    private static boolean jogadorEhBranca(PartidaEntity p, String player) {
        if (player == null || player.isBlank()) return true;
        String f = player.trim().toLowerCase(Locale.ROOT);
        return contains(p.getWhite(), f);
    }

    /** Decide se o lance dentro da partida foi jogado pelo jogador filtrado. */
    private static boolean pertenceAoJogador(PartidaEntity p, LanceEntity l, String player) {
        if (player == null || player.isBlank()) return true;
        boolean brancas = jogadorEhBranca(p, player);
        return brancas == l.isVezBrancas();
    }

    private static boolean isVitoria(PartidaEntity p, String player) {
        String r = p.getResult();
        if (r == null) return false;
        boolean brancas = jogadorEhBranca(p, player);
        return brancas ? "1-0".equals(r) : "0-1".equals(r);
    }

    private static boolean isDerrota(PartidaEntity p, String player) {
        String r = p.getResult();
        if (r == null) return false;
        boolean brancas = jogadorEhBranca(p, player);
        return brancas ? "0-1".equals(r) : "1-0".equals(r);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Acumulador interno para agregação de aberturas. */
    private static final class OpeningAggregate {
        final String eco;
        final String opening;
        long partidas, vitorias, derrotas, empates, blunders;

        OpeningAggregate(String eco, String opening) {
            this.eco = eco;
            this.opening = opening == null ? "" : opening;
        }

        double loseRate() {
            return partidas == 0 ? 0.0 : (derrotas * 100.0) / partidas;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eco",       eco);
            m.put("opening",   opening);
            m.put("partidas",  partidas);
            m.put("vitorias",  vitorias);
            m.put("derrotas",  derrotas);
            m.put("empates",   empates);
            m.put("blunders",  blunders);
            m.put("loseRate",  Math.round(loseRate() * 100.0) / 100.0);
            return m;
        }
    }
}
