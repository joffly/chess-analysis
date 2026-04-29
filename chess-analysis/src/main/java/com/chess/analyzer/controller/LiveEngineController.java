package com.chess.analyzer.controller;

import com.chess.analyzer.model.StockfishResult;
import com.chess.analyzer.service.StockfishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controller SSE para análise ao vivo com Stockfish (MultiPV).
 *
 * <h2>Endpoints</h2>
 * <pre>
 * GET  /api/engine/stream?fen=&amp;depth=18&amp;lines=3  → SSE stream
 * POST /api/engine/stop                           → cancela o stream corrente
 * </pre>
 *
 * <h2>Eventos SSE emitidos</h2>
 * <ul>
 *   <li>{@code ENGINE_UPDATE} – resultado intermediário de uma profundidade</li>
 *   <li>{@code ENGINE_DONE}   – análise completa na profundidade máxima</li>
 *   <li>{@code ENGINE_ERROR}  – falha (Stockfish não pronto, IOException, etc.)</li>
 * </ul>
 *
 * <h2>Payload ENGINE_UPDATE / ENGINE_DONE</h2>
 * <pre>
 * {
 *   "depth": 18,
 *   "lines": [
 *     { "rank": 1, "evalStr": "+0.35", "eval": 0.35,   "uciMoves": ["e2e4","e7e5"] },
 *     { "rank": 2, "evalStr": "+0.12", "eval": 0.12,   "uciMoves": ["d2d4","d7d5"] },
 *     { "rank": 3, "evalStr": "-M3",   "mateIn": -3,   "uciMoves": ["a1a8"] }
 *   ]
 * }
 * </pre>
 *
 * <h2>Estratégia de progressividade</h2>
 * <p>A análise percorre checkpoints de profundidade {@code [6, 9, 12, 15, 18, 21, 24]}
 * até {@code maxDepth}, emitindo um {@code ENGINE_UPDATE} a cada passo.
 * Isso garante resultados visíveis imediatamente, melhorando progressivamente.</p>
 *
 * <h2>Cancelamento</h2>
 * <p>Um contador {@code streamVersion} (AtomicLong) é incrementado a cada novo
 * request. A thread de análise verifica o contador antes de emitir cada resultado.
 * Como {@link StockfishService#analyzeMultiPV} é {@code synchronized}, a análise
 * do checkpoint corrente termina normalmente, e o cancelamento é detectado logo
 * após — sem deadlock.</p>
 */
@RestController
public class LiveEngineController {

    private static final Logger log = LoggerFactory.getLogger(LiveEngineController.class);

    /** Profundidades intermediárias utilizadas na análise progressiva. */
    private static final int[] DEPTH_CHECKPOINTS = { 6, 9, 12, 15, 18, 21, 24 };

    private final StockfishService stockfishService;

    /** Versão atual do stream — incrementada a cada novo request para cancelar streams antigos. */
    private final AtomicLong streamVersion = new AtomicLong(0);

    /** Emitter SSE ativo no momento. */
    private final AtomicReference<SseEmitter> activeEmitter = new AtomicReference<>();

    public LiveEngineController(StockfishService stockfishService) {
        this.stockfishService = stockfishService;
    }

    // ── Endpoints ────────────────────────────────────────────────────────────

    /**
     * Abre um stream SSE de análise ao vivo para a posição FEN fornecida.
     * Qualquer stream anterior é cancelado automaticamente.
     *
     * @param fen   posição FEN a analisar
     * @param depth profundidade máxima (1–30; padrão 18)
     * @param lines número de linhas PV (1–5; padrão 3)
     */
    @GetMapping(value = "/api/engine/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String fen,
            @RequestParam(defaultValue = "18") int depth,
            @RequestParam(defaultValue = "3") int lines) {

        // ── Cancela stream anterior ──────────────────────────────────────────
        SseEmitter old = activeEmitter.getAndSet(null);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }

        final long myVersion  = streamVersion.incrementAndGet();
        final int  maxDepth   = Math.min(Math.max(depth, 1), 30);
        final int  numLines   = Math.min(Math.max(lines, 1), 5);

        // Timeout generoso; o cliente fecha a conexão quando a análise terminar.
        SseEmitter emitter = new SseEmitter(300_000L);
        activeEmitter.set(emitter);

        emitter.onCompletion(() -> activeEmitter.compareAndSet(emitter, null));
        emitter.onTimeout(() -> {
            activeEmitter.compareAndSet(emitter, null);
            emitter.complete();
        });

        // ── Thread virtual de análise ────────────────────────────────────────
        Thread.ofVirtual()
              .name("live-engine-v" + myVersion)
              .start(() -> runAnalysis(emitter, myVersion, fen, maxDepth, numLines));

        return emitter;
    }

    /**
     * Cancela o stream SSE ativo (se houver).
     */
    @PostMapping("/api/engine/stop")
    public ResponseEntity<Map<String, Object>> stopEngine() {
        streamVersion.incrementAndGet();
        SseEmitter old = activeEmitter.getAndSet(null);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Análise ──────────────────────────────────────────────────────────────

    private void runAnalysis(SseEmitter emitter, long myVersion,
                              String fen, int maxDepth, int numLines) {
        try {
            if (!stockfishService.isReady()) {
                send(emitter, "ENGINE_ERROR",
                     "{\"message\":\"Stockfish não está pronto. Configure-o primeiro.\"}");
                emitter.complete();
                return;
            }

            int[] depths = buildDepthSequence(maxDepth);

            for (int d : depths) {
                if (streamVersion.get() != myVersion) return; // cancelado

                log.debug("live-engine depth={} fen={}", d, fen);
                StockfishResult result = stockfishService.analyzeMultiPV(fen, d, numLines);

                if (streamVersion.get() != myVersion) return; // cancelado após análise

                send(emitter, "ENGINE_UPDATE", buildPayload(result, d));
            }

            if (streamVersion.get() == myVersion) {
                send(emitter, "ENGINE_DONE", "{}");
                emitter.complete();
            }

        } catch (Exception e) {
            log.warn("Erro na análise ao vivo (v{}): {}", myVersion, e.getMessage());
            if (streamVersion.get() == myVersion) {
                try {
                    send(emitter, "ENGINE_ERROR",
                         "{\"message\":\"" + e.getMessage().replace("\"", "'") + "\"}");
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void send(SseEmitter emitter, String event, String data) throws Exception {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    /**
     * Constrói a sequência de profundidades: todos os checkpoints menores que
     * {@code maxDepth}, seguidos do próprio {@code maxDepth}.
     * <p>Ex.: maxDepth=18 → [6, 9, 12, 15, 18]</p>
     */
    static int[] buildDepthSequence(int maxDepth) {
        List<Integer> seq = new ArrayList<>();
        for (int d : DEPTH_CHECKPOINTS) {
            if (d < maxDepth) seq.add(d);
        }
        seq.add(maxDepth);
        return seq.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Serializa um {@link StockfishResult} em JSON para o evento SSE. */
    private static String buildPayload(StockfishResult result, int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"depth\":").append(depth).append(",\"lines\":[");

        List<StockfishResult.PvLine> pvLines = result.pvLines();
        if (pvLines == null || pvLines.isEmpty()) {
            // fallback: converte resultado principal em linha única
            appendLine(sb, 1, result.eval(), result.mateIn(), result.pv());
        } else {
            for (int i = 0; i < pvLines.size(); i++) {
                StockfishResult.PvLine pv = pvLines.get(i);
                if (i > 0) sb.append(",");
                appendLine(sb, pv.pvIndex(), pv.eval(), pv.mateIn(), pv.moves());
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, int rank,
                                    Double eval, Integer mateIn, List<String> moves) {
        sb.append("{\"rank\":").append(rank);
        sb.append(",\"evalStr\":\"").append(formatEval(eval, mateIn)).append("\"");
        if (mateIn != null) {
            sb.append(",\"mateIn\":").append(mateIn);
        } else if (eval != null) {
            sb.append(",\"eval\":").append(String.format("%.4f", eval));
        }
        sb.append(",\"uciMoves\":[");
        if (moves != null) {
            for (int j = 0; j < moves.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(moves.get(j)).append("\"");
            }
        }
        sb.append("]}");
    }

    /** Formata avaliação na notação "+0.35" / "-M3" / "+M5". */
    private static String formatEval(Double eval, Integer mateIn) {
        if (mateIn != null) return (mateIn > 0 ? "+M" : "-M") + Math.abs(mateIn);
        if (eval == null) return "0.00";
        return (eval >= 0 ? "+" : "") + String.format("%.2f", eval);
    }
}
