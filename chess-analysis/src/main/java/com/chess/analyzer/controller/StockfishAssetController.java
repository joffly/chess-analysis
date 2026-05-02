package com.chess.analyzer.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serve os arquivos do Stockfish (build WASM single-thread) ao navegador,
 * fazendo download sob demanda da release oficial do GitHub e cacheando
 * em disco para requisições subsequentes.
 *
 * <h2>Por que existe</h2>
 * <ul>
 *   <li>jsDelivr serve os .js do pacote {@code stockfish} com {@code Content-Type: text/plain},
 *       o que faz o {@code importScripts()} em Workers (com checagem estrita
 *       de MIME) falhar nos navegadores modernos.</li>
 *   <li>GitHub Releases ({@code github.com/.../releases/download/...}) redireciona
 *       para {@code objects.githubusercontent.com} sem cabeçalho
 *       {@code Access-Control-Allow-Origin}, o que bloqueia qualquer carregamento
 *       cross-origin pelo navegador.</li>
 * </ul>
 *
 * <h2>Como funciona</h2>
 * <p>O endpoint {@code GET /cdn/stockfish/{file}} entrega o binário same-origin
 * com {@code Content-Type: application/javascript} ou {@code application/wasm}.
 * Na primeira requisição faz o download do GitHub Release v18.0.0 (acesso
 * server-to-server, sem CORS) e salva em
 * {@code ${java.io.tmpdir}/chess-analyzer-stockfish/}; nas próximas serve
 * direto do cache em disco.</p>
 */
@RestController
public class StockfishAssetController {

    private static final Logger log = LoggerFactory.getLogger(StockfishAssetController.class);

    /** Release oficial Stockfish.js v18.0.0 (NNUE, Chess.com sponsored). */
    private static final String BASE_URL = "https://github.com/nmrugg/stockfish.js/releases/download/v18.0.0/";

    /** Whitelist de arquivos serváveis — evita SSRF/path-traversal. */
    private static final Set<String> ALLOWED = Set.of(
            "stockfish-18-lite-single.js",
            "stockfish-18-lite-single.wasm",
            "stockfish-18-single.js",
            "stockfish-18-single.wasm",
            "stockfish-18-lite.js",
            "stockfish-18-lite.wasm",
            "stockfish-18.js",
            "stockfish-18.wasm",
            "stockfish-18-asm.js"
    );

    private static final MediaType WASM_TYPE = MediaType.parseMediaType("application/wasm");
    private static final MediaType JS_TYPE   = MediaType.parseMediaType("application/javascript");

    private final Path cacheDir = Paths.get(System.getProperty("java.io.tmpdir"), "chess-analyzer-stockfish");

    /**
     * HEAD: usado pelo frontend para detectar se a URL está disponível.
     * Retorna 200 sem disparar o download.
     */
    @RequestMapping(value = "/cdn/stockfish/{file:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable String file) {
        return ALLOWED.contains(file)
                ? ResponseEntity.ok().contentType(mediaTypeFor(file)).build()
                : ResponseEntity.notFound().build();
    }

    /**
     * GET: serve o arquivo (download + cache na primeira chamada).
     */
    @GetMapping("/cdn/stockfish/{file:.+}")
    public ResponseEntity<byte[]> serve(@PathVariable String file) {
        if (!ALLOWED.contains(file)) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path cached = ensureCached(file);
            byte[] data = Files.readAllBytes(cached);

            return ResponseEntity.ok()
                    .contentType(mediaTypeFor(file))
                    // Cache agressivo: o conteúdo é versionado pela URL (v18.0.0)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=2592000, immutable")
                    .header("X-Stockfish-Source", "github-release-v18.0.0")
                    .body(data);

        } catch (IOException e) {
            log.error("Falha ao servir asset Stockfish '{}': {}", file, e.getMessage(), e);
            String msg = "// Erro ao baixar " + file + " do GitHub: " + e.getMessage();
            return ResponseEntity.status(502)
                    .contentType(JS_TYPE)
                    .body(msg.getBytes());
        }
    }

    // ── Internos ────────────────────────────────────────────────────────────

    private Path ensureCached(String file) throws IOException {
        Files.createDirectories(cacheDir);
        Path cached = cacheDir.resolve(file);
        if (Files.exists(cached) && Files.size(cached) > 0) {
            return cached;
        }

        String url = BASE_URL + file;
        log.info("Baixando Stockfish asset: {} → {}", url, cached);

        // Usa HttpURLConnection para seguir redirect 302 (GitHub → objects.githubusercontent.com).
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "chess-analyzer/1.0 (+stockfish-asset-proxy)");

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " ao buscar " + url);
        }

        Path tmp = Files.createTempFile(cacheDir, file + ".part-", "");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        log.info("Cacheado {} ({} bytes)", cached.getFileName(), Files.size(cached));
        return cached;
    }

    private static MediaType mediaTypeFor(String file) {
        return file.endsWith(".wasm") ? WASM_TYPE : JS_TYPE;
    }
}
