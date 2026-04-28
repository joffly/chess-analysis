package com.chess.analyzer.service;

import com.chess.analyzer.entity.LanceEntity;
import com.chess.analyzer.model.PuzzleDto;
import com.chess.analyzer.model.PuzzleEvalRequest;
import com.chess.analyzer.model.PuzzleEvalResponse;
import com.chess.analyzer.model.StockfishResult;
import com.chess.analyzer.repository.PuzzleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço responsável por:
 * 1. Buscar puzzles (blunders) por abertura (ECO) no banco de dados.
 * 2. Avaliar o lance do usuário via Stockfish e compará-lo ao melhor lance.
 */
@Service
public class PuzzleService {

    private static final Logger log = LoggerFactory.getLogger(PuzzleService.class);

    /** Diferença mínima de avaliação (em peões) para considerar um lance "ruim". */
    private static final double GOOD_MOVE_THRESHOLD = 0.5;

    /** Profundidade de análise para avaliação de lances do usuário. */
    private static final int PUZZLE_DEPTH = 18;

    private final PuzzleRepository puzzleRepository;
    private final StockfishPoolService stockfishPool;

    public PuzzleService(PuzzleRepository puzzleRepository, StockfishPoolService stockfishPool) {
        this.puzzleRepository = puzzleRepository;
        this.stockfishPool    = stockfishPool;
    }

    // ── Busca de Puzzles ─────────────────────────────────────────────────────

    /**
     * Retorna lista de puzzles (blunders) para a abertura informada.
     *
     * @param eco Código ECO (ex: "B20", "C41")
     * @return lista de DTOs prontos para a UI
     */
    public List<PuzzleDto> findPuzzlesByEco(String eco) {
        List<LanceEntity> blunders = puzzleRepository.findBlundersByEco(eco.trim());
        log.info("ECO={} → {} puzzles encontrados", eco, blunders.size());
        return blunders.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Avaliação do Lance do Usuário ────────────────────────────────────────

    /**
     * Avalia o lance do usuário contra o melhor lance registrado no puzzle.
     * Se o Stockfish estiver disponível, faz uma avaliação real dos dois lances.
     * Caso contrário, usa a comparação direta com o bestMove armazenado.
     *
     * @param req requisição contendo FEN, lance do usuário e melhor lance
     * @return resposta com avaliação detalhada
     */
    public PuzzleEvalResponse evaluateMove(PuzzleEvalRequest req) {
        String fen       = req.fen();
        String userMove  = req.userMove()  != null ? req.userMove().toLowerCase().trim()  : "";
        String bestMove  = req.bestMove()  != null ? req.bestMove().toLowerCase().trim()  : "";

        // Avaliação via Stockfish (se disponível)
        if (stockfishPool.isStarted()) {
            return evaluateWithStockfish(fen, userMove, bestMove);
        }

        // Fallback: comparação direta com o bestMove armazenado
        return evaluateByComparison(userMove, bestMove);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PuzzleEvalResponse evaluateWithStockfish(String fen, String userMove, String bestMove) {
        try {
            // 1. Avalia a posição do melhor lance
            StockfishResult bestResult = stockfishPool.analyze(fen, PUZZLE_DEPTH);
            double bestEval = bestResult.eval();

            // 2. Aplica o lance do usuário e avalia a posição resultante
            String fenAfterUser = applyMoveToFen(fen, userMove);
            double userEval;
            List<String> pvAfterBest = bestResult.pv();

            if (fenAfterUser != null) {
                StockfishResult userResult = stockfishPool.analyze(fenAfterUser, PUZZLE_DEPTH);
                // A avaliação do FEN após o lance está na perspectiva do próximo jogador — inverter
                userEval = -userResult.eval();
            } else {
                // Lance inválido
                return new PuzzleEvalResponse(
                        false, null, bestEval, null,
                        userMove, bestMove,
                        "❌ Lance inválido nessa posição.",
                        pvAfterBest
                );
            }

            double evalDiff = bestEval - userEval;
            boolean good = evalDiff <= GOOD_MOVE_THRESHOLD;

            String message = buildMessage(good, evalDiff, userMove, bestMove);

            return new PuzzleEvalResponse(
                    good, userEval, bestEval, evalDiff,
                    userMove, bestMove,
                    message,
                    pvAfterBest
            );
        } catch (Exception e) {
            log.error("Erro ao avaliar lance: {}", e.getMessage(), e);
            return evaluateByComparison(userMove, bestMove);
        }
    }

    private PuzzleEvalResponse evaluateByComparison(String userMove, String bestMove) {
        boolean good = userMove.equalsIgnoreCase(bestMove);
        String message = good
                ? "✅ Lance correto! Você encontrou o melhor lance."
                : "❌ Lance incorreto. O melhor lance era: " + bestMove;
        return new PuzzleEvalResponse(good, null, null, null, userMove, bestMove, message, List.of());
    }

    private String buildMessage(boolean good, double evalDiff, String userMove, String bestMove) {
        if (good) {
            if (userMove.equalsIgnoreCase(bestMove)) {
                return "✅ Excelente! Você encontrou o melhor lance!";
            }
            return "✅ Bom lance! A diferença de avaliação é pequena (" + String.format("%.2f", evalDiff) + " peões).";
        }
        return "❌ Lance ruim (blunder)! Perdeu " + String.format("%.2f", evalDiff) + " peões. O melhor era: " + bestMove;
    }

    /**
     * Aplica um lance UCI a um FEN usando a biblioteca chess.js no servidor.
     * Como não temos chess.js no backend, usamos o Stockfish para obter o FEN resultante:
     * enviamos a posição + o lance e pedimos 1 nó de análise para "confirmar" o lance.
     *
     * Retorna null se o lance for ilegal.
     */
    private String applyMoveToFen(String fen, String uciMove) {
        try {
            StockfishResult result = stockfishPool.analyzeWithMove(fen, uciMove, 1);
            return result != null ? result.fenAfterMove() : null;
        } catch (Exception e) {
            log.warn("Não foi possível aplicar lance {} ao FEN {}: {}", uciMove, fen, e.getMessage());
            return null;
        }
    }

    private PuzzleDto toDto(LanceEntity lance) {
        List<String> pv = lance.getVariantePrincipal() != null
                ? Arrays.asList(lance.getVariantePrincipal().split(" "))
                : List.of();

        String gameTitle = lance.getPartida() != null ? lance.getPartida().titulo() : "Partida desconhecida";

        return new PuzzleDto(
                lance.getId(),
                lance.getFenAntes(),
                lance.getUci(),
                lance.getSan(),
                lance.getMelhorLance(),
                null,   // evalBefore não armazenado diretamente; poderia ser obtido consultando o lance anterior
                lance.getEval(),
                lance.isVezBrancas(),
                lance.getNumeroLance(),
                gameTitle,
                pv
        );
    }
}
