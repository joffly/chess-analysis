package com.chess.analyzer.service;

import com.chess.analyzer.config.AppProperties;
import com.chess.analyzer.exception.LichessApiException;
import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.LichessImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Serviço responsável por baixar partidas de um usuário do Lichess,
 * persisti-las no banco de dados e carregá-las em memória para navegação.
 *
 * <h2>Fluxo</h2>
 * <ol>
 *   <li>Monta a URI da API do Lichess com os filtros fornecidos
 *       ({@code sort=dateDesc} garante ordem da mais recente para a mais antiga).</li>
 *   <li>Faz um GET autenticado (se token configurado) e recebe o PGN completo.</li>
 *   <li>Parseia o PGN via {@link PgnService#loadFromString}.</li>
 *   <li>Adiciona a tag {@code __fonte_pgn__} com valor {@code lichess:<username>}.</li>
 *   <li>Carrega as partidas em memória via {@link GameAnalysisService#setGames}
 *       para que fiquem disponíveis na UI imediatamente.</li>
 *   <li>Persiste via {@link PartidaSaveService} (upsert — insere ou atualiza).</li>
 * </ol>
 *
 * <h2>Autenticação</h2>
 * <p>Configure {@code chess.lichess-api-token} com um Personal Access Token
 * gerado em <a href="https://lichess.org/account/oauth/token">lichess.org/account/oauth/token</a>.
 * Sem token a API funciona para partidas públicas mas com limite de 15 req/min.</p>
 */
@Service
public class LichessImportService {

    private static final Logger log = LoggerFactory.getLogger(LichessImportService.class);

    /** URL base da API pública do Lichess. */
    private static final String LICHESS_BASE_URL = "https://lichess.org";

    private final PgnService          pgnService;
    private final PartidaSaveService  partidaSaveService;
    private final GameAnalysisService analysisService;
    private final AppProperties        appProperties;
    private final RestClient           restClient;

    public LichessImportService(PgnService pgnService,
                                PartidaSaveService partidaSaveService,
                                GameAnalysisService analysisService,
                                AppProperties appProperties) {
        this.pgnService        = pgnService;
        this.partidaSaveService = partidaSaveService;
        this.analysisService    = analysisService;
        this.appProperties      = appProperties;
        this.restClient = RestClient.builder()
                .baseUrl(LICHESS_BASE_URL)
                .defaultHeader("Accept", "application/x-chess-pgn")
                .build();
    }

    /**
     * Importa, persiste e carrega em memória as partidas de um usuário do Lichess.
     *
     * <p>A ordenação é sempre <b>mais recente primeiro</b> ({@code sort=dateDesc}).</p>
     *
     * @param username  login do usuário no Lichess (obrigatório)
     * @param max       limite máximo de partidas ({@code null} ou {@code 0} usa o valor
     *                  configurado em {@code chess.lichess-max-games}; {@code 0} = sem limite)
     * @param perfType  filtro de modalidade: {@code bullet}, {@code blitz}, {@code rapid},
     *                  {@code classical}, {@code correspondence}, etc. {@code null} = todas
     * @param rated     {@code true} = somente ranqueadas, {@code false} = somente casuais,
     *                  {@code null} = ambas
     * @return resumo do resultado, incluindo os summaries das partidas para a UI
     * @throws LichessApiException se a API do Lichess retornar erro HTTP ou ocorrer falha de rede
     */
    public LichessImportResult importarPartidas(String username,
                                                Integer max,
                                                String  perfType,
                                                Boolean rated) {

        log.info("Iniciando importação do Lichess — usuário: '{}', max: {}, perfType: {}, rated: {}",
                username, max, perfType, rated);

        // ── 1. Monta URI ──────────────────────────────────────────────────────
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString("/api/games/user/{username}")
                .queryParam("tags",     "true")       // inclui todas as tags PGN
                .queryParam("clocks",   "false")      // sem anotações de relógio (reduz payload)
                .queryParam("evals",    "false")      // sem avaliações do servidor (feitas localmente)
                .queryParam("sort",     "dateDesc");  // mais recentes primeiro

        int effectiveMax = (max != null && max > 0) ? max : appProperties.lichessMaxGames();
        if (effectiveMax > 0) {
            uriBuilder.queryParam("max", effectiveMax);
        }
        if (perfType != null && !perfType.isBlank()) {
            uriBuilder.queryParam("perfType", perfType);
        }
        if (rated != null) {
            uriBuilder.queryParam("rated", rated);
        }

        var uri = uriBuilder.buildAndExpand(username).toUri();
        log.debug("URI Lichess: {}", uri);

        // ── 2. Chama a API ────────────────────────────────────────────────────
        String pgnContent;
        try {
            String token = appProperties.lichessApiToken();
            pgnContent = restClient.get()
                    .uri(uri)
                    .headers(h -> {
                        if (token != null && !token.isBlank()) {
                            h.setBearerAuth(token);
                        }
                    })
                    .retrieve()
                    .body(String.class);

        } catch (HttpClientErrorException.NotFound e) {
            throw new LichessApiException(
                    "Usuário '" + username + "' não encontrado no Lichess.", e);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new LichessApiException(
                    "Limite de requisições da API do Lichess atingido. Aguarde e tente novamente.", e);
        } catch (RestClientException e) {
            throw new LichessApiException(
                    "Erro ao acessar a API do Lichess: " + e.getMessage(), e);
        }

        // ── 3. Verifica conteúdo ──────────────────────────────────────────────
        if (pgnContent == null || pgnContent.isBlank()) {
            log.info("Lichess não retornou partidas para o usuário '{}'.", username);
            return LichessImportResult.vazio(username);
        }

        // ── 4. Parseia o PGN ──────────────────────────────────────────────────
        List<GameData> games;
        try {
            games = pgnService.loadFromString(pgnContent);
        } catch (Exception e) {
            log.error("Falha ao parsear PGN retornado pelo Lichess para '{}': {}",
                    username, e.getMessage(), e);
            throw new LichessApiException(
                    "Erro ao parsear as partidas do usuário '" + username + "': " + e.getMessage(), e);
        }

        if (games.isEmpty()) {
            log.info("PGN do Lichess não contém partidas válidas para '{}'.", username);
            return LichessImportResult.vazio(username);
        }

        log.info("{} partida(s) obtidas do Lichess para '{}'. Carregando em memória e persistindo…",
                games.size(), username);

        // ── 5. Adiciona tag de fonte ──────────────────────────────────────────
        String fontePgn = "lichess:" + username;
        List<GameData> gamesComFonte = games.stream()
                .map(g -> g.withFontePgn(fontePgn))
                .toList();

        // ── 6. Carrega em memória para navegação imediata na UI ───────────────
        // Substitui qualquer conjunto de partidas previamente carregado.
        analysisService.setGames(gamesComFonte);
        log.debug("{} partida(s) carregadas no GameAnalysisService.", gamesComFonte.size());

        // ── 7. Persiste no banco (upsert) ─────────────────────────────────────
        // onlyAnalyzed = false → salva todas as partidas mesmo sem análise Stockfish
        List<PartidaSaveService.SaveResult> results =
                partidaSaveService.salvarPartidasAnalisadas(gamesComFonte, false);

        long criadas     = results.stream().filter(r -> "CRIADA".equals(r.status())).count();
        long atualizadas = results.stream().filter(r -> "ATUALIZADA".equals(r.status())).count();
        long ignoradas   = results.stream().filter(r -> r.status().startsWith("IGNORADA")).count();

        log.info("Importação concluída para '{}': {} criadas, {} atualizadas, {} ignoradas.",
                username, criadas, atualizadas, ignoradas);

        // ── 8. Monta summaries para a UI ──────────────────────────────────────
        List<GameData.Summary> summaries = gamesComFonte.stream()
                .map(GameData::toSummary)
                .toList();

        return new LichessImportResult(
                username, results.size(), criadas, atualizadas, ignoradas, results, summaries);
    }
}
