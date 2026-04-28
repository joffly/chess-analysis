package com.chess.analyzer.service;

import com.chess.analyzer.model.StockfishResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gerencia um pool de processos Stockfish para análise paralela.
 * Cada instância é independente e thread-safe, permitindo análise
 * simultânea de múltiplas posições.
 *
 * Ciclo de vida:
 *   start(path, poolSize) → analyze(fen, depth) [concorrente] → close()
 */
@Service
public class StockfishPoolService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StockfishPoolService.class);

    /** Pool de instâncias Stockfish */
    private final List<StockfishInstance> pool = new ArrayList<>();

    /** Fila de instâncias disponíveis para análise */
    private BlockingQueue<StockfishInstance> available;

    /** Estado do pool */
    private volatile boolean started = false;
    private volatile int poolSize = 0;

    /** Lock para operações de inicialização */
    private final ReentrantLock initLock = new ReentrantLock();

    /**
     * Instância individual do Stockfish com seu próprio processo.
     * Todos os métodos são synchronized para garantir sequencialidade UCI.
     */
    private static class StockfishInstance implements Closeable {
        private final int id;
        private Process process;
        private BufferedWriter writer;
        private BufferedReader reader;
        private volatile boolean ready = false;
        private final AtomicBoolean busy = new AtomicBoolean(false);

        StockfishInstance(int id) {
            this.id = id;
        }

        public synchronized void start(String executablePath) throws IOException {
            close();

            log.debug("Iniciando Stockfish #{}: {}", id, executablePath);
            ProcessBuilder pb = new ProcessBuilder(executablePath);
            pb.redirectErrorStream(true);
            process = pb.start();

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Handshake UCI
            send("uci");
            waitFor("uciok", 10_000);

            // Opções de desempenho (ajustadas para multi-thread)
            send("setoption name Hash value 128");
            send("setoption name Threads value 1"); // Cada instância usa 1 thread

            send("isready");
            waitFor("readyok", 10_000);

            ready = true;
            log.debug("Stockfish #{} pronto.", id);
        }

        public synchronized StockfishResult analyze(String fen, int depth) throws IOException {
            ensureReady();
            send("position fen " + fen);
            send("go depth " + depth);
            StockfishResult result = collectResult(fen);
            log.trace("Stockfish #{} analisou FEN={} → {}", id, fen, result);
            return result;
        }

        /**
         * Analisa a posição resultante após aplicar um lance UCI ao FEN informado.
         * Usa o comando UCI: {@code position fen <fen> moves <uciMove>}
         *
         * @param fen     FEN da posição base
         * @param uciMove lance UCI a ser aplicado (ex: "e2e4")
         * @param depth   profundidade de análise
         * @return resultado da análise da posição após o lance
         */
        public synchronized StockfishResult analyzeWithMove(String fen, String uciMove, int depth) throws IOException {
            ensureReady();
            send("position fen " + fen + " moves " + uciMove);
            send("go depth " + depth);
            // O FEN para normalização de perspectiva é derivado do turno original invertido
            String fenAfterMove = deriveFenSideToMove(fen);
            StockfishResult result = collectResult(fenAfterMove);
            log.trace("Stockfish #{} analisou FEN={} moves={} → {}", id, fen, uciMove, result);
            return result;
        }

        /**
         * Deriva um FEN mínimo com o lado a mover invertido, usado apenas para
         * normalizar a perspectiva da avaliação retornada pelo Stockfish.
         */
        private String deriveFenSideToMove(String fen) {
            String[] parts = fen.split("\\s+");
            if (parts.length < 2) return fen;
            String newSide = parts[1].equals("w") ? "b" : "w";
            parts[1] = newSide;
            return String.join(" ", parts);
        }

        public synchronized void stop() {
            if (ready) {
                try {
                    send("stop");
                } catch (IOException ignored) {
                }
            }
        }

        public boolean isReady() {
            return ready;
        }

        public boolean isBusy() {
            return busy.get();
        }

        public void setBusy(boolean b) {
            busy.set(b);
        }

        private void send(String cmd) throws IOException {
            log.trace("→ SF #{}: {}", id, cmd);
            writer.write(cmd);
            writer.newLine();
            writer.flush();
        }

        private String waitFor(String keyword, long timeoutMs) throws IOException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            String line;
            while ((line = reader.readLine()) != null) {
                log.trace("← SF #{}: {}", id, line);
                if (line.contains(keyword))
                    return line;
                if (System.currentTimeMillis() > deadline)
                    throw new IOException("Timeout aguardando '%s' do Stockfish #%d".formatted(keyword, id));
            }
            throw new IOException("Stream fechada antes de receber '%s'".formatted(keyword));
        }

        private StockfishResult collectResult(String fen) throws IOException {
            String lastInfoLine = null;
            String line;
            while ((line = reader.readLine()) != null) {
                log.trace("← SF #{}: {}", id, line);
                if (line.startsWith("info") && line.contains("score"))
                    lastInfoLine = line;
                if (line.startsWith("bestmove"))
                    return parseResult(lastInfoLine, line, fen);
            }
            throw new IOException("Stream do Stockfish #%d encerrada sem 'bestmove'".formatted(id));
        }

        private StockfishResult parseResult(String infoLine, String bestMoveLine, String fen) {
            String bestMove = null;
            if (bestMoveLine != null) {
                String[] p = bestMoveLine.split("\\s+");
                if (p.length > 1 && !"(none)".equals(p[1]))
                    bestMove = p[1];
            }

            if (infoLine == null)
                return new StockfishResult(0.0, null, bestMove, List.of());

            String[] tokens = infoLine.split("\\s+");
            double eval = 0.0;
            Integer mateIn = null;
            List<String> pv = new ArrayList<>();

            for (int i = 0; i < tokens.length; i++) {
                switch (tokens[i]) {
                    case "cp" -> eval = safeInt(tokens, i + 1) / 100.0;
                    case "mate" -> mateIn = safeInt(tokens, i + 1);
                    case "pv" -> pv = new ArrayList<>(Arrays.asList(tokens).subList(i + 1, tokens.length));
                }
            }

            // Normaliza para perspectiva Brancas
            boolean blackToMove = fen.split("\\s+")[1].equals("b");
            if (blackToMove) {
                eval = -eval;
                if (mateIn != null)
                    mateIn = -mateIn;
            }

            return new StockfishResult(eval, mateIn, bestMove, List.copyOf(pv));
        }

        private int safeInt(String[] tokens, int idx) {
            try {
                return Integer.parseInt(tokens[idx]);
            } catch (Exception e) {
                return 0;
            }
        }

        private void ensureReady() throws IOException {
            if (!ready)
                throw new IOException("Stockfish #%d não está inicializado.".formatted(id));
        }

        @Override
        public synchronized void close() {
            ready = false;
            if (process != null && process.isAlive()) {
                try {
                    send("quit");
                } catch (IOException ignored) {
                }
                process.destroyForcibly();
                process = null;
            }
            log.debug("Stockfish #{} encerrado.", id);
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────

    /**
     * Inicia o pool com o número especificado de instâncias.
     *
     * @param executablePath caminho para o executável Stockfish
     * @param poolSize       número de instâncias no pool (1-8 recomendado)
     */
    public void start(String executablePath, int poolSize) throws IOException {
        initLock.lock();
        try {
            if (started) {
                log.warn("Pool já está iniciado. Reiniciando...");
                close();
            }

            this.poolSize = Math.clamp(poolSize, 1, 8);
            log.info("Iniciando pool Stockfish com {} instância(s): {}", this.poolSize, executablePath);

            available = new ArrayBlockingQueue<>(this.poolSize);

            for (int i = 0; i < this.poolSize; i++) {
                StockfishInstance instance = new StockfishInstance(i);
                instance.start(executablePath);
                pool.add(instance);
                available.offer(instance);
            }

            started = true;
            log.info("Pool Stockfish pronto com {} instância(s).", this.poolSize);
        } finally {
            initLock.unlock();
        }
    }

    /**
     * Analisa uma posição usando uma instância disponível do pool.
     * Método bloqueante: aguarda até que uma instância esteja disponível.
     *
     * @param fen   FEN da posição a analisar
     * @param depth profundidade de busca
     * @return resultado da análise
     * @throws InterruptedException se a thread for interrompida enquanto aguarda
     * @throws IOException          se ocorrer erro na comunicação com Stockfish
     */
    public StockfishResult analyze(String fen, int depth) throws InterruptedException, IOException {
        if (!started)
            throw new IOException("Pool não está inicializado. Configure o caminho primeiro.");

        StockfishInstance instance = available.take(); // Bloqueia até estar disponível
        try {
            instance.setBusy(true);
            return instance.analyze(fen, depth);
        } finally {
            instance.setBusy(false);
            available.offer(instance); // Devolve à fila
        }
    }

    /**
     * Analisa a posição resultante após aplicar {@code uciMove} ao {@code fen} informado.
     * Equivale a enviar ao Stockfish: {@code position fen <fen> moves <uciMove>}.
     *
     * <p>Útil para avaliar a qualidade de um lance específico sem precisar calcular
     * o FEN resultante externamente.</p>
     *
     * @param fen     FEN da posição base
     * @param uciMove lance UCI a aplicar (ex: "e2e4")
     * @param depth   profundidade de análise
     * @return resultado da análise após o lance
     * @throws InterruptedException se a thread for interrompida enquanto aguarda instância
     * @throws IOException          se ocorrer erro na comunicação com Stockfish
     */
    public StockfishResult analyzeWithMove(String fen, String uciMove, int depth)
            throws InterruptedException, IOException {
        if (!started)
            throw new IOException("Pool não está inicializado. Configure o caminho primeiro.");

        StockfishInstance instance = available.take();
        try {
            instance.setBusy(true);
            return instance.analyzeWithMove(fen, uciMove, depth);
        } finally {
            instance.setBusy(false);
            available.offer(instance);
        }
    }

    /**
     * Interrompe todas as análises em andamento.
     */
    public void stopAll() {
        log.info("Parando todas as análises do pool...");
        for (StockfishInstance instance : pool) {
            if (instance.isBusy()) {
                instance.stop();
            }
        }
    }

    public boolean isStarted() {
        return started;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getAvailableCount() {
        return available != null ? available.size() : 0;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("started", started);
        status.put("poolSize", poolSize);
        status.put("available", getAvailableCount());
        status.put("busy", poolSize - getAvailableCount());
        return status;
    }

    @Override
    public void close() {
        initLock.lock();
        try {
            log.info("Fechando pool Stockfish...");
            started = false;

            for (StockfishInstance instance : pool) {
                try {
                    instance.close();
                } catch (Exception e) {
                    log.error("Erro ao fechar instância #{}", instance.id, e);
                }
            }

            pool.clear();
            available = null;
            poolSize = 0;

            log.info("Pool Stockfish fechado.");
        } finally {
            initLock.unlock();
        }
    }
}
