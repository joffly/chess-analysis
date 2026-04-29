package com.chess.analyzer.service;

import com.chess.analyzer.model.StockfishResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gerencia um processo Stockfish persistente via protocolo UCI.
 *
 * Todos os métodos públicos são {@code synchronized} pois o Stockfish
 * é single-threaded e espera pares comando/resposta estritamente sequenciais.
 *
 * Ciclo de vida:
 *   start(path)  →  analyze(fen, depth) [N vezes]  →  close()
 */
@Service
public class StockfishService implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(StockfishService.class);

    private Process        process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private volatile boolean ready = false;

    // ── Lifecycle ────────────────────────────────────────────────

    /**
     * Inicia (ou reinicia) o processo Stockfish.
     *
     * @param executablePath caminho absoluto ou relativo para o executável
     */
    public synchronized void start(String executablePath) throws IOException {
        close(); // encerra instância anterior se existir

        log.info("Iniciando Stockfish: {}", executablePath);
        ProcessBuilder pb = new ProcessBuilder(executablePath);
        pb.redirectErrorStream(true);
        process = pb.start();

        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // Handshake UCI
        send("uci");
        waitFor("uciok", 10_000);

        // Opções de desempenho
        send("setoption name Hash value 256");
        send("setoption name Threads value 2");

        send("isready");
        waitFor("readyok", 10_000);

        ready = true;
        log.info("Stockfish pronto.");
    }

    /**
     * Analisa uma posição (FEN) até a profundidade especificada.
     * A avaliação retornada é sempre da perspectiva das Brancas.
     *
     * @param fen   FEN da posição a analisar
     * @param depth profundidade de busca (ex: 15-20 para qualidade razoável)
     */
    public synchronized StockfishResult analyze(String fen, int depth) throws IOException {
        ensureReady();
        send("position fen " + fen);
        send("go depth " + depth);
        StockfishResult result = collectResult(fen);
        log.debug("Análise FEN={} → {}", fen, result);
        return result;
    }

    /**
     * Analisa uma posição com múltiplas linhas PV (MultiPV).
     * As avaliações retornadas são sempre da perspectiva das Brancas.
     * O modo MultiPV é revertido para 1 ao término.
     *
     * @param fen   FEN da posição a analisar
     * @param depth profundidade de busca
     * @param numPV número de linhas PV a calcular (ex: 4)
     */
    public synchronized StockfishResult analyzeMultiPV(String fen, int depth, int numPV) throws IOException {
        ensureReady();
        send("setoption name MultiPV value " + numPV);
        send("isready");
        waitFor("readyok", 5_000);

        send("position fen " + fen);
        send("go depth " + depth);

        StockfishResult result;
        try {
            result = collectResultMultiPV(fen, numPV);
        } finally {
            // Restaura MultiPV para 1 independente de erro
            try {
                send("setoption name MultiPV value 1");
            } catch (IOException ignored) {}
        }

        log.debug("analyzeMultiPV FEN={} numPV={} → {}", fen, numPV, result);
        return result;
    }

    /**
     * Interrompe a busca em andamento (cancela a análise corrente).
     */
    public synchronized void stop() {
        if (ready) {
            try { send("stop"); } catch (IOException ignored) {}
        }
    }

    public boolean isReady() { return ready; }

    // ── Comunicação UCI ──────────────────────────────────────────

    private void send(String cmd) throws IOException {
        log.trace("→ SF: {}", cmd);
        writer.write(cmd);
        writer.newLine();
        writer.flush();
    }

    /** Lê linhas até encontrar {@code keyword} ou estourar o timeout. */
    private String waitFor(String keyword, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String line;
        while ((line = reader.readLine()) != null) {
            log.trace("← SF: {}", line);
            if (line.contains(keyword)) return line;
            if (System.currentTimeMillis() > deadline)
                throw new IOException("Timeout aguardando '%s' do Stockfish".formatted(keyword));
        }
        throw new IOException("Stream fechada antes de receber '%s'".formatted(keyword));
    }

    /**
     * Lê linhas "info" até receber "bestmove".
     * A última linha info com "score" determina a avaliação final.
     */
    private StockfishResult collectResult(String fen) throws IOException {
        String lastInfoLine = null;
        String line;
        while ((line = reader.readLine()) != null) {
            log.trace("← SF: {}", line);
            if (line.startsWith("info") && line.contains("score")) lastInfoLine = line;
            if (line.startsWith("bestmove")) return parseResult(lastInfoLine, line, fen);
        }
        throw new IOException("Stream do Stockfish encerrada sem 'bestmove'");
    }

    /**
     * Lê linhas "info" em modo MultiPV, rastreando a última linha por índice PV,
     * e retorna um {@link StockfishResult} com todas as linhas PV preenchidas.
     */
    private StockfishResult collectResultMultiPV(String fen, int numPV) throws IOException {
        // Chave: índice multipv (1-based); Valor: última linha info recebida para esse índice
        Map<Integer, String> lastInfoPerPv = new LinkedHashMap<>();
        String bestMoveLine = null;
        String line;

        while ((line = reader.readLine()) != null) {
            log.trace("← SF: {}", line);
            if (line.startsWith("info") && line.contains("score")) {
                int pvIdx = extractMultiPvIndex(line);
                lastInfoPerPv.put(pvIdx, line);
            }
            if (line.startsWith("bestmove")) {
                bestMoveLine = line;
                break;
            }
        }
        if (bestMoveLine == null) {
            throw new IOException("Stream do Stockfish encerrada sem 'bestmove' (MultiPV)");
        }

        // Resultado principal a partir da PV 1
        String mainInfoLine = lastInfoPerPv.get(1);
        StockfishResult main = parseResult(mainInfoLine, bestMoveLine, fen);

        // Verifica perspectiva (para normalização)
        boolean blackToMove = fen.split("\\s+").length > 1 && "b".equals(fen.split("\\s+")[1]);

        // Constrói lista de PvLine (ordenada por índice)
        List<StockfishResult.PvLine> pvLines = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : new TreeMap<>(lastInfoPerPv).entrySet()) {
            int pvIdx    = entry.getKey();
            String info  = entry.getValue();
            if (info == null) continue;

            String[]     tokens    = info.split("\\s+");
            double       lineEval  = 0.0;
            Integer      lineMate  = null;
            List<String> lineMoves = new ArrayList<>();

            for (int i = 0; i < tokens.length; i++) {
                switch (tokens[i]) {
                    case "cp"   -> lineEval  = safeInt(tokens, i + 1) / 100.0;
                    case "mate" -> lineMate  = safeInt(tokens, i + 1);
                    case "pv"   -> lineMoves = new ArrayList<>(
                                       Arrays.asList(tokens).subList(i + 1, tokens.length));
                }
            }

            // Normaliza para perspectiva Brancas
            if (blackToMove) {
                lineEval = -lineEval;
                if (lineMate != null) lineMate = -lineMate;
            }

            pvLines.add(new StockfishResult.PvLine(
                pvIdx,
                lineMate != null ? null : lineEval,   // eval nulo quando há mate
                lineMate,
                List.copyOf(lineMoves)
            ));
        }

        return new StockfishResult(
            main.eval(), main.mateIn(), main.bestMove(), main.pv(), null, List.copyOf(pvLines));
    }

    /**
     * Converte a linha "info" e a linha "bestmove" em {@link StockfishResult}.
     *
     * Formato info:
     *   info depth 15 ... score cp 45 ... pv e2e4 e7e5 g1f3
     *   info depth  5 ... score mate 3 ... pv e1g1
     *
     * O Stockfish retorna a avaliação do lado que vai jogar.
     * Normalizamos para a perspectiva das Brancas (flip se for vez das Pretas).
     */
    private StockfishResult parseResult(String infoLine, String bestMoveLine, String fen) {
        // Extrai bestMove
        String bestMove = null;
        if (bestMoveLine != null) {
            String[] p = bestMoveLine.split("\\s+");
            if (p.length > 1 && !"(none)".equals(p[1])) bestMove = p[1];
        }

        if (infoLine == null) return new StockfishResult(0.0, null, bestMove, List.of());

        String[] tokens = infoLine.split("\\s+");
        double       eval   = 0.0;
        Integer      mateIn = null;
        List<String> pv     = new ArrayList<>();

        for (int i = 0; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "cp"   -> eval   = safeInt(tokens, i + 1) / 100.0;
                case "mate" -> mateIn = safeInt(tokens, i + 1);
                case "pv"   -> pv     = new ArrayList<>(
                                            Arrays.asList(tokens).subList(i + 1, tokens.length));
            }
        }

        // Normaliza para perspectiva Brancas
        boolean blackToMove = fen.split("\\s+")[1].equals("b");
        if (blackToMove) {
            eval = -eval;
            if (mateIn != null) mateIn = -mateIn;
        }

        return new StockfishResult(eval, mateIn, bestMove, List.copyOf(pv));
    }

    /** Extrai o índice {@code multipv N} de uma linha info; retorna 1 se ausente. */
    private int extractMultiPvIndex(String infoLine) {
        String[] tokens = infoLine.split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if ("multipv".equals(tokens[i])) {
                try { return Integer.parseInt(tokens[i + 1]); } catch (Exception ignored) {}
            }
        }
        return 1;
    }

    private int safeInt(String[] tokens, int idx) {
        try { return Integer.parseInt(tokens[idx]); }
        catch (Exception e) { return 0; }
    }

    private void ensureReady() throws IOException {
        if (!ready) throw new IOException(
                "Stockfish não está inicializado. Configure o caminho primeiro.");
    }

    @Override
    public synchronized void close() {
        ready = false;
        if (process != null && process.isAlive()) {
            try { send("quit"); } catch (IOException ignored) {}
            process.destroyForcibly();
            process = null;
        }
        log.info("Stockfish encerrado.");
    }
}
