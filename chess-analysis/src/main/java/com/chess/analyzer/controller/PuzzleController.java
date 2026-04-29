package com.chess.analyzer.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.chess.analyzer.config.AppProperties;
import com.chess.analyzer.model.MoveRequest;
import com.chess.analyzer.model.MoveResponse;
import com.chess.analyzer.model.StockfishResult;
import com.chess.analyzer.service.BoardService;
import com.chess.analyzer.service.GameAnalysisService;
import com.chess.analyzer.service.PuzzleDbService;
import com.chess.analyzer.service.PuzzleService;
import com.chess.analyzer.service.StockfishService;

/**
 * Controlador da aba "Puzzles" (problemas de xadrez).
 *
 * <p>
 * <strong>Estratégia DB-first com fallback para memória:</strong>
 * </p>
 * <ol>
 * <li>Tenta buscar ECOs/puzzles no banco de dados (partidas salvas e
 * analisadas).</li>
 * <li>Se o banco estiver vazio, usa as partidas carregadas em memória via PGN
 * na sessão atual da aba principal.</li>
 * <li>Se nenhuma fonte tiver dados, orienta o usuário sobre como popular.</li>
 * </ol>
 *
 * Endpoints:
 * <ul>
 * <li>{@code GET  /puzzles} — página HTML</li>
 * <li>{@code GET  /api/puzzles/ecos} — ECOs com blunders (DB-first)</li>
 * <li>{@code GET  /api/puzzles?eco=XXX} — puzzles do ECO informado
 * (DB-first)</li>
 * <li>{@code POST /api/puzzles/evaluate} — avalia o lance do usuário com
 * Stockfish</li>
 * </ul>
 */
@Controller
public class PuzzleController {

	private static final Logger log = LoggerFactory.getLogger(PuzzleController.class);

	private final PuzzleDbService puzzleDbService;
	private final PuzzleService puzzleService;
	private final BoardService boardService;
	private final StockfishService stockfish;
	private final GameAnalysisService analysisService;
	private final AppProperties appProperties;

	public PuzzleController(PuzzleDbService puzzleDbService, PuzzleService puzzleService, BoardService boardService,
			StockfishService stockfish, GameAnalysisService analysisService, AppProperties appProperties) {
		this.puzzleDbService = puzzleDbService;
		this.puzzleService = puzzleService;
		this.boardService = boardService;
		this.stockfish = stockfish;
		this.analysisService = analysisService;
		this.appProperties = appProperties;
	}

	// ── UI ──────────────────────────────────────────────────────────────────

	@GetMapping("/puzzles")
	public String puzzlesPage() {
		return "puzzles";
	}

	// ── ECOs ────────────────────────────────────────────────────────────────

	/**
	 * Lista as aberturas (ECO) que possuem lances blunder.
	 *
	 * <p>
	 * <strong>Fonte 1 — Banco de dados:</strong> partidas persistidas com
	 * {@code lance.blunder = true} e {@code lance.analisado = true}.
	 * </p>
	 *
	 * <p>
	 * <strong>Fonte 2 — Memória (fallback):</strong> partidas carregadas via PGN na
	 * sessão atual, quando o banco não possui dados analisados.
	 * </p>
	 */
	@GetMapping("/api/puzzles/ecos")
	@ResponseBody
	public List<Map<String, Object>> listEcos() {
		// 1. Banco de dados (fonte primária)
		try {
			List<Map<String, Object>> dbEcos = puzzleDbService.listEcos();
			if (!dbEcos.isEmpty()) {
				log.info("listEcos() → {} ECO(s) do banco de dados", dbEcos.size());
				return dbEcos;
			}
		} catch (Exception e) {
			log.error("Erro ao listar ECOs do banco: {}", e.getMessage(), e);
		}

		// 2. Memória — PGN carregado na sessão atual (fallback)
		List<Map<String, Object>> memEcos = puzzleService.listEcos();
		log.info("listEcos() → {} ECO(s) da memória (fallback)", memEcos.size());
		return memEcos;
	}

	// ── Puzzles por ECO ─────────────────────────────────────────────────────

	/**
	 * Retorna os puzzles (posições blunder) para o ECO informado.
	 *
	 * <p>
	 * Segue a mesma estratégia DB-first com fallback para memória.
	 * </p>
	 */
	@GetMapping("/api/puzzles")
	@ResponseBody
	public ResponseEntity<?> getPuzzles(@RequestParam String eco) {

		// 1. Banco de dados (fonte primária)
		try {
			List<Map<String, Object>> dbPuzzles = puzzleDbService.getPuzzlesByEco(eco);
			if (!dbPuzzles.isEmpty()) {
				log.info("getPuzzles({}) → {} puzzle(s) do banco de dados", eco, dbPuzzles.size());
				return ResponseEntity
						.ok(Map.of("ok", true, "eco", eco, "count", dbPuzzles.size(), "puzzles", dbPuzzles));
			}
		} catch (Exception e) {
			log.error("Erro ao buscar puzzles do banco para ECO {}: {}", eco, e.getMessage(), e);
		}

		// 2. Memória — fallback para partidas carregadas via PGN na sessão
		if (!analysisService.getGames().isEmpty()) {
			List<Map<String, Object>> memPuzzles = puzzleService.getPuzzlesByEco(eco);
			log.info("getPuzzles({}) → {} puzzle(s) da memória (fallback)", eco, memPuzzles.size());
			return ResponseEntity.ok(Map.of("ok", true, "eco", eco, "count", memPuzzles.size(), "puzzles", memPuzzles));
		}

		// 3. Sem dados em nenhuma fonte
		return ResponseEntity.badRequest().body(Map.of("ok", false, "message",
				"Nenhum puzzle encontrado para " + eco + ". Carregue um PGN na aba principal e analise as partidas."));
	}

	// ── Avaliação do lance do usuário ────────────────────────────────────────

	/**
	 * Recebe o lance jogado pelo usuário, valida-o pelo BoardService, analisa a
	 * posição resultante com o Stockfish e classifica a qualidade do lance.
	 *
	 * <p>
	 * Body esperado:
	 * {@code { fen, from, to, promotion, evalBefore, bestMove, depth }}
	 * </p>
	 */
	@PostMapping("/api/puzzles/evaluate")
	@ResponseBody
	public ResponseEntity<?> evaluate(@RequestBody EvaluateRequest req) {
		if (!stockfish.isReady()) {

			String stockfishPath = appProperties.stockfishPath();
			try {
				stockfish.start(stockfishPath);
			} catch (IOException e) {
				return ResponseEntity.badRequest()
						.body(Map.of("ok", false, "message", "Stockfish não conectado. Configure-o na aba principal."));
			}
		}

		// 1. Aplica o lance do usuário (valida legalidade)
		MoveResponse mv = boardService.applyMove(new MoveRequest(req.fen(), req.from(), req.to(), req.promotion()));
		if (!mv.ok()) {
			return ResponseEntity.badRequest().body(Map.of("ok", false, "message", mv.message()));
		}

		// 2. Analisa a nova posição com o Stockfish
		int depth = req.depth() == null || req.depth() <= 0 ? analysisService.getAnalysisDepth() : req.depth();
		StockfishResult sf;
		try {
			sf = stockfish.analyze(mv.fen(), depth);
		} catch (Exception e) {
			log.error("Erro Stockfish: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError()
					.body(Map.of("ok", false, "message", "Erro Stockfish: " + e.getMessage()));
		}

		// 3. Classifica a qualidade do lance
		boolean whiteToMove = req.fen() != null && req.fen().split("\\s+").length > 1
				&& req.fen().split("\\s+")[1].equals("w");
		String userUci = req.from() + req.to() + (req.promotion() != null ? req.promotion() : "");
		boolean isBest = req.bestMove() != null && req.bestMove().equalsIgnoreCase(userUci);

		Map<String, Object> classification = puzzleService.classifyMove(
				req.evalBefore() == null ? 0.0 : req.evalBefore(), sf.mateIn() != null ? null : sf.eval(), sf.mateIn(),
				whiteToMove, isBest);

		// 4. Resposta
		return ResponseEntity.ok(Map.of("ok", true, "playedFen", mv.fen(), "playedSan", mv.san(), "playedUci", userUci,
				"check", mv.check(), "checkmate", mv.checkmate(), "stockfishBestMove",
				sf.bestMove() == null ? "" : sf.bestMove(), "stockfishPv",
				sf.pv() == null ? List.of() : sf.pv().stream().limit(5).toList(), "classification", classification));
	}

	/** Payload do {@code POST /api/puzzles/evaluate}. */
	public record EvaluateRequest(String fen, String from, String to, String promotion, Double evalBefore,
			String bestMove, Integer depth) {
	}
}
