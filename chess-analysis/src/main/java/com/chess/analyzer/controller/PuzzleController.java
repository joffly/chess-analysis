package com.chess.analyzer.controller;

import com.chess.analyzer.model.PuzzleDto;
import com.chess.analyzer.model.PuzzleEvalRequest;
import com.chess.analyzer.model.PuzzleEvalResponse;
import com.chess.analyzer.service.PuzzleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints REST para a aba de Treino de Táticas (Puzzles).
 *
 * GET  /api/puzzles?eco=B20    → lista puzzles (blunders) da abertura
 * POST /api/puzzles/evaluate   → avalia o lance do usuário via Stockfish
 */
@RestController
@RequestMapping("/api/puzzles")
public class PuzzleController {

    private final PuzzleService puzzleService;

    public PuzzleController(PuzzleService puzzleService) {
        this.puzzleService = puzzleService;
    }

    /**
     * Retorna puzzles (blunders) para o código ECO informado.
     *
     * @param eco  código ECO (ex: "B20", "C41") — case-insensitive
     * @return lista de PuzzleDto ou 400 se o ECO não for informado
     */
    @GetMapping
    public ResponseEntity<?> getPuzzles(@RequestParam String eco) {
        if (eco == null || eco.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", "Informe o código ECO."));
        }
        List<PuzzleDto> puzzles = puzzleService.findPuzzlesByEco(eco);
        if (puzzles.isEmpty()) {
            return ResponseEntity.ok(
                    Map.of("ok", true, "count", 0, "puzzles", List.of(),
                           "message", "Nenhum puzzle encontrado para ECO=" + eco.toUpperCase())
            );
        }
        return ResponseEntity.ok(Map.of("ok", true, "count", puzzles.size(), "puzzles", puzzles));
    }

    /**
     * Avalia o lance do usuário no puzzle.
     *
     * Body JSON: { "fen": "...", "userMove": "e2e4", "bestMove": "d2d4" }
     *
     * @param req requisição com FEN, lance do usuário e melhor lance de referência
     * @return PuzzleEvalResponse com avaliação, mensagem e linha principal
     */
    @PostMapping("/evaluate")
    public ResponseEntity<PuzzleEvalResponse> evaluate(@RequestBody PuzzleEvalRequest req) {
        PuzzleEvalResponse response = puzzleService.evaluateMove(req);
        return ResponseEntity.ok(response);
    }
}
