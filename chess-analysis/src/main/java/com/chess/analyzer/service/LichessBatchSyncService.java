package com.chess.analyzer.service;

import com.chess.analyzer.config.AppProperties;
import com.chess.analyzer.exception.LichessApiException;
import com.chess.analyzer.model.GameData;
import com.chess.analyzer.repository.PartidaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Job batch agendado que sincroniza partidas do Lichess para o banco local
 * <em>já analisadas</em> pelo Stockfish.
 *
 * <h2>Fluxo</h2>
 * <ol>
 *   <li>Aguarda o cron disparar ({@code chess.batch.cron}).</li>
 *   <li>Faz GET autenticado em {@code /api/games/user/{user}} no Lichess
 *       e recebe o PGN bruto (mais recentes primeiro).</li>
 *   <li>Parseia via {@link PgnService#loadFromString}.</li>
 *   <li>Filtra: mantém apenas partidas cujo {@code gameId} <em>não</em>
 *       existe ainda em {@link PartidaRepository}.</li>
 *   <li>Carrega as partidas novas em {@link GameAnalysisService} e dispara
 *       a análise síncrona com a profundidade configurada
 *       ({@code chess.batch.depth}, default 10).</li>
 *   <li>Após o término da análise, persiste tudo via
 *       {@link PartidaSaveService}.</li>
 *   <li>Invalida o {@link RadarCacheService cache do dashboard} para que
 *       a próxima requisição ao Radar reflita os novos dados.</li>
 * </ol>
 *
 * <h2>Propriedades</h2>
 * <pre>
 * chess.batch.enabled   = true|false        (default: false — opt-in)
 * chess.batch.user      = &lt;lichess-user&gt;   (obrigatório quando habilitado)
 * chess.batch.depth     = 10                (profundidade Stockfish)
 * chess.batch.cron      = 0 0 3 * * *       (cron Spring; default 03:00 todo dia)
 * chess.batch.max-games = 50                (limite por execução; 0 = sem limite)
 * </pre>
 *
 * <h2>Concorrência</h2>
 * <p>Apenas uma execução por vez é permitida via {@link AtomicBoolean}.
 * Se o cron disparar enquanto uma sincronização anterior ainda roda,
 * a nova execução é ignorada com WARN.</p>
 *
 * <p>Se o usuário estiver com o Stockfish ocupado em uma análise manual via
 * UI, a tentativa de iniciar análise pelo batch retornará {@code false} e
 * o job apenas logará um aviso, tentando novamente no próximo ciclo.</p>
 */
@Service
public class LichessBatchSyncService {

    private static final Logger log = LoggerFactory.getLogger(LichessBatchSyncService.class);
    private static final String LICHESS_BASE_URL = "https://lichess.org";

    /** Tempo máximo (ms) que o job aguardará a análise terminar (default 30 min). */
    private static final long ANALYSIS_TIMEOUT_MS = 30L * 60L * 1000L;

    private final PgnService pgnService;
    private final PartidaRepository partidaRepository;
    private final PartidaSaveService partidaSaveService;
    private final GameAnalysisService analysisService;
    private final StockfishPoolService stockfishPool;
    private final RadarCacheService radarCache;
    private final AppProperties appProperties;
    private final RestClient restClient;

    // ── Configuração específica do batch (via @Value para não inflar AppProperties) ──
    private final boolean batchEnabled;
    private final String batchUser;
    private final int batchDepth;
    private final int batchMaxGames;

    /** Trava simples para impedir execuções sobrepostas do mesmo job. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LichessBatchSyncService(PgnService pgnService,
                                   PartidaRepository partidaRepository,
                                   PartidaSaveService partidaSaveService,
                                   GameAnalysisService analysisService,
                                   StockfishPoolService stockfishPool,
                                   RadarCacheService radarCache,
                                   AppProperties appProperties,
                                   @Value("${chess.batch.enabled:false}") boolean batchEnabled,
                                   @Value("${chess.batch.user:}") String batchUser,
                                   @Value("${chess.batch.depth:10}") int batchDepth,
                                   @Value("${chess.batch.max-games:50}") int batchMaxGames) {
        this.pgnService = pgnService;
        this.partidaRepository = partidaRepository;
        this.partidaSaveService = partidaSaveService;
        this.analysisService = analysisService;
        this.stockfishPool = stockfishPool;
        this.radarCache = radarCache;
        this.appProperties = appProperties;
        this.batchEnabled = batchEnabled;
        this.batchUser = batchUser == null ? "" : batchUser.trim();
        this.batchDepth = batchDepth > 0 ? batchDepth : 10;
        this.batchMaxGames = batchMaxGames < 0 ? 0 : batchMaxGames;

        this.restClient = RestClient.builder()
                .baseUrl(LICHESS_BASE_URL)
                .defaultHeader("Accept", "application/x-chess-pgn")
                .build();

        log.info("LichessBatchSyncService configurado — enabled={}, user='{}', depth={}, maxGames={}",
                batchEnabled, this.batchUser, this.batchDepth, this.batchMaxGames);
    }

    // ── Disparo agendado ───────────────────────────────────────────────────

    /**
     * Job principal — disparado pelo cron {@code chess.batch.cron}
     * (default 03:00 diariamente).
     *
     * <p>O método é idempotente: se já estiver rodando, retorna sem efeito.
     * Se o batch estiver desabilitado ou sem usuário configurado, também
     * retorna sem efeito.</p>
     */
    @Scheduled(cron = "${chess.batch.cron:0 0 3 * * *}")
    public void scheduledRun() {
        if (!batchEnabled) {
            log.debug("Batch sync desabilitado (chess.batch.enabled=false). Ignorando disparo.");
            return;
        }
        if (batchUser.isBlank()) {
            log.warn("Batch sync habilitado mas chess.batch.user está vazio. Ignorando disparo.");
            return;
        }
        runOnce(batchUser, batchDepth, batchMaxGames);
    }

    /**
     * Execução manual (por exemplo, exposta via endpoint REST). Permite
     * forçar a sincronização sem esperar o cron, útil para testes.
     *
     * @return número de partidas novas analisadas e salvas (ou {@code -1}
     *         se não rodou por estar bloqueado/desabilitado).
     */
    public int runOnce(String user, int depth, int maxGames) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Sync batch já em execução; descartando nova tentativa.");
            return -1;
        }
        try {
            return executar(user, depth, maxGames);
        } catch (Exception e) {
            log.error("Falha geral no batch sync para '{}': {}", user, e.getMessage(), e);
            return -1;
        } finally {
            running.set(false);
        }
    }

    // ── Núcleo do processamento ────────────────────────────────────────────

    private int executar(String user, int depth, int maxGames) {
        log.info("⏱ Iniciando sync batch — user='{}', depth={}, maxGames={}",
                user, depth, maxGames == 0 ? "∞" : maxGames);

        // 1. Baixa o PGN bruto do Lichess
        String pgn = baixarPgnLichess(user, maxGames);
        if (pgn == null || pgn.isBlank()) {
            log.info("Sync batch: nenhum PGN retornado para '{}'.", user);
            return 0;
        }

        // 2. Parseia
        List<GameData> games;
        try {
            games = pgnService.loadFromString(pgn);
        } catch (Exception e) {
            log.error("Sync batch: erro ao parsear PGN do Lichess para '{}': {}", user, e.getMessage());
            return 0;
        }
        log.info("Sync batch: {} partida(s) recebida(s) do Lichess para '{}'.", games.size(), user);

        // 3. Filtra: apenas as que ainda NÃO estão no banco
        String fontePgn = "lichess-batch:" + user;
        List<GameData> novas = games.stream()
                .filter(g -> partidaRepository.findByGameId(g.getGameId()).isEmpty())
                .map(g -> g.withFontePgn(fontePgn))
                .toList();

        if (novas.isEmpty()) {
            log.info("Sync batch: nenhuma partida nova para '{}' (todas já estão no banco).", user);
            return 0;
        }
        log.info("Sync batch: {} partida(s) NOVA(s) para analisar (depth={}).", novas.size(), depth);

        // 4. Carrega em memória e dispara análise
        analysisService.setGames(novas);
        analysisService.setAnalysisDepth(depth);

        if (!stockfishPool.isStarted()) {
            log.warn("Sync batch: pool do Stockfish não está inicializado. Configure o caminho antes de iniciar a análise.");
            return -1;
        }

        boolean started = analysisService.startAnalysis();
        if (!started) {
            log.warn("Sync batch: análise não iniciou (já há outra em execução?). Tentará no próximo ciclo.");
            return -1;
        }

        // 5. Aguarda término (poll)
        if (!aguardarTerminoAnalise()) {
            log.warn("Sync batch: análise excedeu o timeout de {} ms. Salvando o que está pronto.",
                    ANALYSIS_TIMEOUT_MS);
        }

        // 6. Persiste apenas as partidas totalmente analisadas
        List<PartidaSaveService.SaveResult> resultados =
                partidaSaveService.salvarPartidasAnalisadas(novas, /*onlyAnalyzed=*/ true);

        long criadas = resultados.stream().filter(r -> "CRIADA".equals(r.status())).count();
        log.info("Sync batch: persistência concluída — {} criada(s), {} total resultado(s).",
                criadas, resultados.size());

        // 7. Invalida cache do radar para refletir os novos dados imediatamente
        radarCache.invalidateAll();

        return (int) criadas;
    }

    /**
     * Aguarda o {@link GameAnalysisService} terminar a análise, fazendo polling
     * a cada 1 segundo até o timeout.
     *
     * @return {@code true} se terminou dentro do timeout
     */
    private boolean aguardarTerminoAnalise() {
        long deadline = System.currentTimeMillis() + ANALYSIS_TIMEOUT_MS;
        while (analysisService.isAnalyzing()) {
            if (System.currentTimeMillis() > deadline) return false;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    // ── Download Lichess (sem reusar LichessImportService para não mexer no estado da UI) ──

    private String baixarPgnLichess(String username, int maxGames) {
        UriComponentsBuilder uri = UriComponentsBuilder
                .fromUriString("/api/games/user/{username}")
                .queryParam("tags", "true")
                .queryParam("clocks", "false")
                .queryParam("evals", "false")
                .queryParam("sort", "dateDesc");

        if (maxGames > 0) {
            uri.queryParam("max", maxGames);
        }

        String token = appProperties.lichessApiToken();
        try {
            return restClient.get()
                    .uri(uri.buildAndExpand(username).toUri())
                    .headers(h -> {
                        if (token != null && !token.isBlank()) h.setBearerAuth(token);
                    })
                    .retrieve()
                    .body(String.class);

        } catch (HttpClientErrorException.NotFound e) {
            throw new LichessApiException(
                    "Usuário '" + username + "' não encontrado no Lichess.", e);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new LichessApiException(
                    "Limite de requisições da API do Lichess atingido.", e);
        } catch (RestClientException e) {
            throw new LichessApiException(
                    "Erro ao acessar a API do Lichess: " + e.getMessage(), e);
        }
    }

    // ── Getters para diagnóstico ───────────────────────────────────────────

    public boolean isEnabled() { return batchEnabled; }
    public boolean isRunning() { return running.get(); }
    public String getBatchUser() { return batchUser; }
    public int getBatchDepth() { return batchDepth; }
}
