package com.chess.analyzer.controller;

import com.chess.analyzer.exception.LichessApiException;
import com.chess.analyzer.model.LichessImportResult;
import com.chess.analyzer.service.LichessImportService;
import com.chess.analyzer.service.PartidaSaveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller REST para importação de partidas do Lichess.
 *
 * <h2>Endpoint</h2>
 * <pre>POST /api/lichess/import</pre>
 *
 * <h2>Parâmetros de query</h2>
 * <table>
 *   <tr><th>Parâmetro</th><th>Obrigatório</th><th>Descrição</th></tr>
 *   <tr><td>username</td><td>Sim</td><td>Login do usuário no Lichess</td></tr>
 *   <tr><td>max</td><td>Não</td><td>Número máximo de partidas (padrão: config {@code chess.lichess-max-games})</td></tr>
 *   <tr><td>perfType</td><td>Não</td><td>Modalidade: bullet, blitz, rapid, classical, correspondence</td></tr>
 *   <tr><td>rated</td><td>Não</td><td>true = apenas ranqueadas | false = apenas casuais</td></tr>
 * </table>
 *
 * <h2>Exemplo de uso</h2>
 * <pre>
 * POST /api/lichess/import?username=magnuscarlsen&amp;max=50&amp;perfType=blitz&amp;rated=true
 * </pre>
 */
@RestController
@RequestMapping("/api/lichess")
public class LichessController {

    private static final Logger log = LoggerFactory.getLogger(LichessController.class);

    private final LichessImportService lichessImportService;

    public LichessController(LichessImportService lichessImportService) {
        this.lichessImportService = lichessImportService;
    }

    /**
     * Baixa e persiste no banco de dados todas as partidas de um usuário do Lichess.
     *
     * @param username  login do usuário no Lichess (obrigatório)
     * @param max       limite de partidas a baixar (opcional; 0 = sem limite)
     * @param perfType  filtro de modalidade (opcional)
     * @param rated     filtro de partidas ranqueadas/casuais (opcional)
     * @return JSON com resumo da importação
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importarPartidas(
            @RequestParam String username,
            @RequestParam(required = false) Integer max,
            @RequestParam(required = false) String  perfType,
            @RequestParam(required = false) Boolean rated) {

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("ok", false, "message", "O parâmetro 'username' é obrigatório."));
        }

        try {
            LichessImportResult result =
                    lichessImportService.importarPartidas(username.strip(), max, perfType, rated);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok",          true);
            body.put("username",    result.username());
            body.put("total",       result.total());
            body.put("criadas",     result.criadas());
            body.put("atualizadas", result.atualizadas());
            body.put("ignoradas",   result.ignoradas());
            body.put("games",       result.games());   // summaries para renderização na UI
            body.put("detalhes",    buildDetalhes(result.detalhes()));

            return ResponseEntity.ok(body);

        } catch (LichessApiException e) {
            log.warn("Importação Lichess falhou para '{}': {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("ok", false, "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Erro inesperado na importação Lichess para '{}': {}", username, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    Map.of("ok", false, "message", "Erro interno ao importar partidas: " + e.getMessage()));
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildDetalhes(List<PartidaSaveService.SaveResult> results) {
        return results.stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("index",  r.index());
                    m.put("title",  r.title());
                    m.put("status", r.status());
                    return m;
                })
                .toList();
    }
}
