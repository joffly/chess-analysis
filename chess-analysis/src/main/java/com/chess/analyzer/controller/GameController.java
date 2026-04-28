package com.chess.analyzer.controller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.chess.analyzer.config.AppProperties;
import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.LegalMovesResponse;
import com.chess.analyzer.model.MoveEntry;
import com.chess.analyzer.model.MoveRequest;
import com.chess.analyzer.model.MoveResponse;
import com.chess.analyzer.service.BoardService;
import com.chess.analyzer.service.GameAnalysisService;
import com.chess.analyzer.service.PartidaSaveService;
import com.chess.analyzer.service.PartidaSaveService.SaveResult;
import com.chess.analyzer.service.PgnService;
import com.chess.analyzer.service.StockfishPoolService;

/**
 * Controlador principal — expõe a SPA e todos os endpoints REST/SSE.
 *
 * Endpoints: GET / → UI (SPA Thymeleaf)
 *
 * POST /api/stockfish/configure → inicia o processo Stockfish GET
 * /api/stockfish/status → estado atual do motor
 *
 * POST /api/pgn/load → carrega arquivo PGN (multipart)
 *
 * GET /api/games → lista de resumos das partidas GET /api/games/{index} → dados
 * completos de uma partida
 *
 * GET /api/board/legal-moves → destinos legais para uma peça POST
 * /api/board/move → executa um lance e retorna novo FEN
 *
 * POST /api/analysis/start → inicia análise em background POST
 * /api/analysis/stop → cancela análise GET /api/analysis/events → SSE stream de
 * progresso
 *
 * POST /api/games/save → salva partidas analisadas no banco (upsert)
 *
 * GET /api/export/pgn → download PGN anotado
 */
@Controller
public class GameController {

	private static final Logger log = LoggerFactory.getLogger(GameController.class);

	private final StockfishPoolService stockfishPool;
	private final PgnService pgnService;
	private final GameAnalysisService analysisService;
	private final BoardService boardService;
	private final AppProperties appProperties;
	private final PartidaSaveService partidaSaveService;

	public GameController(StockfishPoolService stockfishPool, PgnService pgnService,
			GameAnalysisService analysisService, BoardService boardService,
			AppProperties appProperties, PartidaSaveService partidaSaveService) {
		this.stockfishPool = stockfishPool;
		this.pgnService = pgnService;
		this.analysisService = analysisService;
		this.boardService = boardService;
		this.appProperties = appProperties;
		this.partidaSaveService = partidaSaveService;
	}

	// ── UI ───────────────────────────────────────────────────────

	@GetMapping("/")
	public String index() {
		return "index";
	}

	// ── Stockfish ────────────────────────────────────────────────

	@PostMapping("/api/stockfish/configure")
	@ResponseBody
	public ResponseEntity<?> configureStockfish(@RequestParam(required = false) String path,
			@RequestParam(defaultValue = "15") int depth, @RequestParam(required = false) Integer poolSize) {
		try {
			String stockfishPath = (path != null && !path.isBlank()) ? path : appProperties.stockfishPath();
			int effectivePoolSize = (poolSize != null && poolSize > 0) ? poolSize : appProperties.stockfishPoolSize();

			if (stockfishPool.isStarted()) {
				stockfishPool.close();
			}
			stockfishPool.start(stockfishPath, effectivePoolSize);
			analysisService.setAnalysisDepth(depth);
			return ResponseEntity
					.ok(Map.of("ok", true, "message", "Pool Stockfish iniciado com %d instância(s) e profundidade %d"
							.formatted(effectivePoolSize, depth)));
		} catch (Exception e) {
			log.error("Erro ao configurar Stockfish: {}", e.getMessage());
			return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
		}
	}

	@GetMapping("/api/stockfish/parametro")
	public ResponseEntity<StockfishConfig> getConfig() {
		return ResponseEntity.ok(new StockfishConfig(appProperties.stockfishPath(), appProperties.analysisDepth(),
				appProperties.analysisTimeLimitMs()));
	}

	@GetMapping("/api/stockfish/status")
	@ResponseBody
	public Map<String, Object> stockfishStatus() {
		Map<String, Object> status = new LinkedHashMap<>();
		status.put("ready", stockfishPool.isStarted());
		status.put("depth", analysisService.getAnalysisDepth());
		status.put("analyzing", analysisService.isAnalyzing());
		if (stockfishPool.isStarted()) {
			status.putAll(stockfishPool.getStatus());
		}
		return status;
	}

	// ── PGN ──────────────────────────────────────────────────────

	@PostMapping("/api/pgn/load")
	@ResponseBody
	public ResponseEntity<?> loadPgn(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty())
			return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Arquivo vazio."));
		try {
			var games = pgnService.load(file);
			if (games.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Nenhuma partida encontrada."));
			}
			analysisService.setGames(games);
			return ResponseEntity.ok(Map.of("ok", true, "count", games.size(), "games",
					games.stream().map(GameData::toSummary).collect(Collectors.toList())));
		} catch (Exception e) {
			log.error("Erro ao carregar PGN: {}", e.getMessage(), e);
			return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
		}
	}

	// ── Games ────────────────────────────────────────────────────

	@GetMapping("/api/games")
	@ResponseBody
	public List<GameData.Summary> listGames() {
		return analysisService.getGames().stream().map(GameData::toSummary).collect(Collectors.toList());
	}

	@GetMapping("/api/games/{index}")
	@ResponseBody
	public ResponseEntity<?> getGame(@PathVariable int index) {
		return analysisService.getGame(index).map(game -> {
			List<Map<String, Object>> moveDtos = new ArrayList<>();
			for (MoveEntry m : game.getMoves()) {
				Map<String, Object> dto = new LinkedHashMap<>();
				dto.put("uci", m.getUci());
				dto.put("san", m.getSan());
				dto.put("fenBefore", m.getFenBefore());
				dto.put("fenAfter", m.getFenAfter());
				dto.put("moveNumber", m.getMoveNumber());
				dto.put("whiteTurn", m.isWhiteTurn());
				dto.put("analyzed", m.isAnalyzed());
				if (m.isAnalyzed()) {
					dto.put("eval", m.getEval());
					dto.put("mateIn", m.getMateIn());
					dto.put("bestMove", m.getBestMove());
					dto.put("pv", m.getPv() != null ? m.getPv().stream().limit(5).toList() : List.of());
					dto.put("evalStr", m.getEvalFormatted());
				}
				moveDtos.add(dto);
			}
			return ResponseEntity.ok(Map.of("index", game.getIndex(), "tags", game.getTags(), "title", game.getTitle(),
					"initialFen", game.getInitialFen(), "moves", moveDtos, "analyzed", game.isFullyAnalyzed()));
		}).orElseGet(() -> ResponseEntity.notFound().build());
	}

	// ── Board interaction ────────────────────────────────────────

	@GetMapping("/api/board/legal-moves")
	@ResponseBody
	public LegalMovesResponse legalMoves(@RequestParam String fen, @RequestParam String from) {
		return boardService.legalMoves(fen, from);
	}

	@PostMapping("/api/board/move")
	@ResponseBody
	public MoveResponse applyMove(@RequestBody MoveRequest req) {
		return boardService.applyMove(req);
	}

	// ── Analysis ─────────────────────────────────────────────────

	@PostMapping("/api/analysis/start")
	@ResponseBody
	public ResponseEntity<?> startAnalysis() {
		if (!stockfishPool.isStarted())
			return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Stockfish não configurado."));
		if (analysisService.getGames().isEmpty())
			return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Nenhuma partida carregada."));
		boolean started = analysisService.startAnalysis();
		return ResponseEntity
				.ok(Map.of("ok", started, "message", started ? "Análise iniciada." : "Análise já em andamento."));
	}

	@PostMapping("/api/analysis/stop")
	@ResponseBody
	public Map<String, Object> stopAnalysis() {
		analysisService.stopAnalysis();
		return Map.of("ok", true);
	}

	@GetMapping(value = "/api/analysis/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public SseEmitter analysisEvents() {
		return analysisService.createEmitter();
	}

	// ── Persistência ──────────────────────────────────────────────

	/**
	 * POST /api/games/save
	 * Salva no banco todas as partidas analisadas (ou todas, se onlyAnalyzed=false).
	 * Aplica upsert: insere novas, atualiza existentes com as avaliações mais recentes.
	 *
	 * Query params:
	 *   onlyAnalyzed (boolean, default=true) — filtra apenas partidas com análise completa
	 */
	@PostMapping("/api/games/save")
	@ResponseBody
	public ResponseEntity<?> saveAnalyzedGames(
			@RequestParam(defaultValue = "true") boolean onlyAnalyzed) {

		List<GameData> games = analysisService.getGames();
		if (games.isEmpty()) {
			return ResponseEntity.badRequest()
					.body(Map.of("ok", false, "message", "Nenhuma partida carregada."));
		}

		List<SaveResult> results = partidaSaveService.salvarPartidasAnalisadas(games, onlyAnalyzed);

		long criadas     = results.stream().filter(r -> r.status().equals("CRIADA")).count();
		long atualizadas = results.stream().filter(r -> r.status().equals("ATUALIZADA")).count();
		long ignoradas   = results.stream().filter(r -> r.status().startsWith("IGNORADA")).count();

		return ResponseEntity.ok(Map.of(
				"ok",          true,
				"criadas",     criadas,
				"atualizadas", atualizadas,
				"ignoradas",   ignoradas,
				"total",       results.size(),
				"detalhes",    results.stream()
									 .map(r -> Map.of(
											 "index",  r.index(),
											 "title",  r.title(),
											 "status", r.status()))
									 .toList()
		));
	}

	// ── Export ───────────────────────────────────────────────────

	@GetMapping("/api/export/pgn")
	public ResponseEntity<byte[]> exportPgn() {
		byte[] bytes = analysisService.exportPgn().getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analyzed.pgn\"")
				.contentType(MediaType.parseMediaType("application/x-chess-pgn")).body(bytes);
	}
}
