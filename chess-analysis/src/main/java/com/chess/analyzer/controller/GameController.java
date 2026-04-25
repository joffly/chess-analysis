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
import com.chess.analyzer.service.PgnService;
import com.chess.analyzer.service.StockfishService;

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
 * GET /api/export/pgn → download PGN anotado
 */
@Controller
public class GameController {

	private static final Logger log = LoggerFactory.getLogger(GameController.class);

	private final StockfishService stockfish;
	private final PgnService pgnService;
	private final GameAnalysisService analysisService;
	private final BoardService boardService;
	private final AppProperties appProperties;

	public GameController(StockfishService stockfish, PgnService pgnService, GameAnalysisService analysisService,
			BoardService boardService, AppProperties appProperties) {
		this.stockfish = stockfish;
		this.pgnService = pgnService;
		this.analysisService = analysisService;
		this.boardService = boardService;
		this.appProperties = appProperties;
	}

	// ── UI ───────────────────────────────────────────────────────

	@GetMapping("/")
	public String index() {
		return "index";
	}

	// ── Stockfish ────────────────────────────────────────────────

	@PostMapping("/api/stockfish/configure")
	@ResponseBody
	public ResponseEntity<?> configureStockfish(@RequestParam String path,
			@RequestParam(defaultValue = "15") int depth) {
		try {
			if (path == null) {
				stockfish.start(appProperties.stockfishPath());
			} else {
				stockfish.start(path);
			}
			analysisService.setAnalysisDepth(depth);
			return ResponseEntity.ok(Map.of("ok", true, "message", "Stockfish conectado com profundidade " + depth));
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
		return Map.of("ready", stockfish.isReady(), "depth", analysisService.getAnalysisDepth(), "analyzing",
				analysisService.isAnalyzing());
	}

	// ── PGN ──────────────────────────────────────────────────────

	@PostMapping("/api/pgn/load")
	@ResponseBody
	public ResponseEntity<?> loadPgn(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty())
			return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Arquivo vazio."));
		try {
			var games = pgnService.load(file);
			if (games.isEmpty())
				return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "Nenhuma partida encontrada."));
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

	/**
	 * Retorna os destinos legais para a peça em {@code from} na posição
	 * {@code fen}. Query params: fen, from
	 */
	@GetMapping("/api/board/legal-moves")
	@ResponseBody
	public LegalMovesResponse legalMoves(@RequestParam String fen, @RequestParam String from) {
		return boardService.legalMoves(fen, from);
	}

	/**
	 * Executa um lance no tabuleiro livre (modo exploração).
	 */
	@PostMapping("/api/board/move")
	@ResponseBody
	public MoveResponse applyMove(@RequestBody MoveRequest req) {
		return boardService.applyMove(req);
	}

	// ── Analysis ─────────────────────────────────────────────────

	@PostMapping("/api/analysis/start")
	@ResponseBody
	public ResponseEntity<?> startAnalysis() {
		if (!stockfish.isReady())
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

	/** SSE stream de progresso da análise. */
	@GetMapping(value = "/api/analysis/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public SseEmitter analysisEvents() {
		return analysisService.createEmitter();
	}

	// ── Export ───────────────────────────────────────────────────

	@GetMapping("/api/export/pgn")
	public ResponseEntity<byte[]> exportPgn() {
		byte[] bytes = analysisService.exportPgn().getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analyzed.pgn\"")
				.contentType(MediaType.parseMediaType("application/x-chess-pgn")).body(bytes);
	}
}
