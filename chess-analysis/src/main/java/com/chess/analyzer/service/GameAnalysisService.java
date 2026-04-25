package com.chess.analyzer.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;
import com.chess.analyzer.model.StockfishResult;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

/**
 * Serviço central que mantém o estado em memória das partidas carregadas e
 * coordena a análise com o Stockfish via virtual threads (Java 21),
 * transmitindo progresso em tempo real por Server-Sent Events (SSE).
 */

@Service
public class GameAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(GameAnalysisService.class);

	private final StockfishPoolService stockfishPool;
	private final PgnService pgnService;

	/** Partidas carregadas (thread-safe para leitura concorrente). */
	private final List<GameData> games = new CopyOnWriteArrayList<>();

	/** Clientes SSE ativos. */
	private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	/** Impede análises simultâneas. */
	private final AtomicBoolean analyzing = new AtomicBoolean(false);

	private volatile int analysisDepth = 15;

	public GameAnalysisService(StockfishPoolService stockfishPool, PgnService pgnService) {
		this.stockfishPool = stockfishPool;
		this.pgnService = pgnService;
	}

	// ── Gerenciamento de estado ──────────────────────────────────

	public void setGames(List<GameData> loaded) {
		games.clear();
		games.addAll(loaded);
		log.info("{} partida(s) carregada(s) em memória.", games.size());
	}

	public List<GameData> getGames() {
		return Collections.unmodifiableList(games);
	}

	public Optional<GameData> getGame(int i) {
		return (i >= 0 && i < games.size()) ? Optional.of(games.get(i)) : Optional.empty();
	}

	public int getAnalysisDepth() {
		return analysisDepth;
	}

	public void setAnalysisDepth(int d) {
		analysisDepth = Math.clamp(d, 1, 30);
	}

	public boolean isAnalyzing() {
		return analyzing.get();
	}

	// ── SSE ──────────────────────────────────────────────────────

	public SseEmitter createEmitter() {
		SseEmitter emitter = new SseEmitter(0L); // sem timeout
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));
		return emitter;
	}

	// ── Análise ──────────────────────────────────────────────────

	/**
	 * Inicia a análise de todas as partidas em uma virtual thread. Só permite uma
	 * análise por vez.
	 *
	 * @return {@code false} se já houver análise em andamento
	 */
	public boolean startAnalysis() {
		if (!analyzing.compareAndSet(false, true)) {
			log.warn("Análise já em andamento.");
			return false;
		}
		Thread.ofVirtual().name("analysis-main").start(() -> {
			try {
				runAnalysis();
			} catch (Exception e) {
				log.error("Erro na análise: {}", e.getMessage(), e);
				// Tratamento de nulo obrigatório para o Map.of()
				String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
				broadcast("ERROR", Map.of("message", errorMessage));
			} finally {
				analyzing.set(false);
				broadcast("COMPLETE", Map.of("totalGames", games.size()));
				log.info("Análise finalizada.");
			}
		});
		return true;
	}

	public void stopAnalysis() {
		if (analyzing.compareAndSet(true, false)) {
			stockfishPool.stopAll();
			broadcast("STOPPED", Map.of());
		}
	}

	// ── Loop de análise ──────────────────────────────────────────

	/**
	 * Executa a análise paralela usando múltiplas virtual threads, uma por instância Stockfish.
	 * Cada lance é analisado concorrentemente respeitando a ordem dentro da partida.
	 */
	private void runAnalysis() throws Exception {
		cleanAnalysisContext();
		int totalPlies = games.stream().mapToInt(g -> g.getMoves().size()).sum();
		AtomicInteger analyzedCounter = new AtomicInteger(0);

		broadcast("START", Map.of("totalGames", games.size(), "totalMoves", totalPlies, "depth", analysisDepth,
				"poolSize", stockfishPool.getPoolSize()));

		// Coleta todas as posições para análise em paralelo
		List<AnalysisTask> tasks = new ArrayList<>();
		for (GameData game : games) {
			List<MoveEntry> moves = game.getMoves();
			for (int mi = 0; mi < moves.size(); mi++) {
				MoveEntry move = moves.get(mi);
				tasks.add(new AnalysisTask(game, mi, move));
			}
		}

		// Executa análises em paralelo usando CompletableFuture com virtual threads
		try {
			List<CompletableFuture<Void>> futures = tasks.stream()
					.map(task -> {
						CompletableFuture<Void> future = new CompletableFuture<>();
						Thread.ofVirtual().start(() -> {
							try {
								executeAnalysis(task, analyzedCounter, totalPlies);
								future.complete(null);
							} catch (Exception e) {
								log.error("Erro ao analisar lance: {}", e.getMessage());
								future.completeExceptionally(e);
							}
						});
						return future;
					})
					.toList();

			// Aguarda todas as tarefas completarem
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		} catch (Exception e) {
			if (e.getCause() instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				log.warn("Análise interrompida.");
				return;
			}
			throw e;
		}

		// Marca todas as partidas como analisadas
		games.forEach(game -> game.setFullyAnalyzed(true));
		games.forEach(game -> broadcast("GAME_DONE",
				Map.of("gameIndex", game.getIndex(), "title", game.getTitle())));
	}

	/**
	 * Tarefa de análise individual para um lance específico.
	 */
	private record AnalysisTask(GameData game, int moveIndex, MoveEntry move) {
	}

	/**
	 * Executa a análise de uma única tarefa e transmite o resultado via SSE.
	 */
	private void executeAnalysis(AnalysisTask task, AtomicInteger counter, int totalPlies)
			throws Exception {
		if (!analyzing.get())
			return;

		GameData game = task.game();
		int mi = task.moveIndex();
		MoveEntry move = task.move();

		StockfishResult r = stockfishPool.analyze(move.getFenBefore(), analysisDepth);

		move.setAnalysis(r.mateIn() != null ? 0.0 : r.eval(), r.mateIn(), r.bestMove(), r.pv());

		int analyzed = counter.incrementAndGet();

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("gameIndex", game.getIndex());
		payload.put("moveIndex", mi);
		payload.put("eval", r.mateIn() != null ? null : r.eval());
		payload.put("mateIn", r.mateIn());
		payload.put("bestMove", uciToSan(move.getFenBefore(), r.bestMove()));
		payload.put("pv", r.pv().stream().limit(5).map(pvUci -> uciToSan(getFenForPv(move.getFenBefore(), r.pv(), pvUci), pvUci)).toList());
		payload.put("evalStr", move.getEvalFormatted());
		payload.put("progress", analyzed * 100 / Math.max(1, totalPlies));
		broadcast("MOVE_ANALYZED", payload);
	}

	private void cleanAnalysisContext() {
		// 1. Resetar flags das partidas de forma declarativa (Stream API)
		games.stream().filter(GameData::isFullyAnalyzed).forEach(g -> g.setFullyAnalyzed(false));

		// 2. Limpar emissores inativos diretamente com removeIf
		emitters.removeIf(emitter -> {
			try {
				// Envia o "ping" invisível
				emitter.send(SseEmitter.event().comment("ping"));
				return false; // Retorna false -> NÃO remove da lista (está ativo)
			} catch (Exception e) {
				log.warn("Emissor inativo removido. Motivo: {}", e.getMessage());
				return true; // Retorna true -> REMOVE da lista (está inativo)
			}
		});
	}

	private static String uciToSan(String fen, String uci) {
		if (uci == null || uci.isBlank())
			return null;

		Board board = new Board();
		board.loadFromFen(fen);

		Square from = Square.valueOf(uci.substring(0, 2).toUpperCase());
		Square to = Square.valueOf(uci.substring(2, 4).toUpperCase());

		PieceType pieceType = board.getPiece(from).getPieceType();
		String fromStr = uci.substring(0, 2).toLowerCase(); // "e2"
		String toStr = uci.substring(2, 4).toLowerCase(); // "e4"

		// Roque: rei move 2 casas na coluna
		if (pieceType == PieceType.KING && Math.abs(uci.charAt(2) - uci.charAt(0)) == 2) {
			return uci.charAt(2) > uci.charAt(0) ? "O-O" : "O-O-O";
		}

		boolean isCapture = board.getPiece(to) != Piece.NONE
				|| (pieceType == PieceType.PAWN && uci.charAt(0) != uci.charAt(2)); // en passant

		StringBuilder san = new StringBuilder();

		if (pieceType == PieceType.PAWN) {
			if (isCapture)
				san.append(fromStr.charAt(0)).append('x');
			san.append(toStr);
			if (uci.length() == 5) // promoção
				san.append('=').append(Character.toUpperCase(uci.charAt(4)));

		} else {
			san.append(pieceType.getSanSymbol());

			// Desambiguação: outras peças do mesmo tipo que também alcançam 'to'
			List<Square> ambiguous = board.legalMoves().stream().filter(m -> m.getTo() == to && m.getFrom() != from)
					.filter(m -> board.getPiece(m.getFrom()).getPieceType() == pieceType).map(Move::getFrom).toList();

			if (!ambiguous.isEmpty()) {
				boolean sameFile = ambiguous.stream()
						.anyMatch(s -> s.toString().toLowerCase().charAt(0) == fromStr.charAt(0));
				boolean sameRank = ambiguous.stream()
						.anyMatch(s -> s.toString().toLowerCase().charAt(1) == fromStr.charAt(1));

				if (!sameFile)
					san.append(fromStr.charAt(0)); // só coluna
				else if (!sameRank)
					san.append(fromStr.charAt(1)); // só linha
				else
					san.append(fromStr); // coluna + linha
			}

			if (isCapture)
				san.append('x');
			san.append(toStr);
		}

		// Executa o lance para detectar xeque / xeque-mate
		Piece promo = Piece.NONE;
		if (uci.length() == 5) {
			Side side = board.getSideToMove();
			promo = switch (uci.charAt(4)) {
			case 'q' -> side == Side.WHITE ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
			case 'r' -> side == Side.WHITE ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
			case 'b' -> side == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
			case 'n' -> side == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
			default -> Piece.NONE;
			};
		}
		board.doMove(new Move(from, to, promo));
		if (board.isKingAttacked())
			san.append(board.legalMoves().isEmpty() ? '#' : '+');

		return san.toString();
	}

	/**
	 * Calcula o FEN resultante após aplicar uma sequência de lances UCI a partir de um FEN inicial.
	 * Usado para converter lances da variante principal (PV) em SAN.
	 */
	private static String getFenForPv(String initialFen, List<String> pv, String targetUci) {
		Board board = new Board();
		board.loadFromFen(initialFen);

		for (String uci : pv) {
			if (uci.equals(targetUci)) {
				return board.getFen();
			}
			try {
				Square from = Square.valueOf(uci.substring(0, 2).toUpperCase());
				Square to = Square.valueOf(uci.substring(2, 4).toUpperCase());
				Piece promo = Piece.NONE;
				if (uci.length() == 5) {
					Side side = board.getSideToMove();
					promo = switch (uci.charAt(4)) {
					case 'q' -> side == Side.WHITE ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
					case 'r' -> side == Side.WHITE ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
					case 'b' -> side == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
					case 'n' -> side == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
					default -> Piece.NONE;
					};
				}
				board.doMove(new Move(from, to, promo));
			} catch (Exception e) {
				// Se houver erro, retorna o FEN atual para tentar conversão parcial
				return board.getFen();
			}
		}
		return board.getFen();
	}

	// ── Broadcast SSE ────────────────────────────────────────────

	private void broadcast(String eventName, Map<String, Object> data) {
		if (emitters.isEmpty())
			return;

		String json = toJson(data);
		List<SseEmitter> deadEmitters = new ArrayList<>();

		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name(eventName).data(json));
			} catch (IOException e) {
				// Adiciona na lista de falhas em vez de remover diretamente
				deadEmitters.add(emitter);
			}
		}

		// Remove todos os emissores inativos de forma thread-safe
		if (!deadEmitters.isEmpty()) {
			emitters.removeAll(deadEmitters);
		}
	}

	/** Serialização JSON mínima sem dependências extras. */
	private String toJson(Map<String, Object> map) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (var e : map.entrySet()) {
			if (!first)
				sb.append(",");
			first = false;
			sb.append("\"").append(e.getKey()).append("\":");
			Object v = e.getValue();
			if (v == null)
				sb.append("null");
			else if (v instanceof String s)
				sb.append("\"").append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
			else if (v instanceof List<?> list) {
				sb.append("[");
				boolean lf = true;
				for (Object item : list) {
					if (!lf)
						sb.append(",");
					lf = false;
					sb.append("\"").append(item).append("\"");
				}
				sb.append("]");
			} else {
				sb.append(v);
			}
		}
		return sb.append("}").toString();
	}

	// ── Export ───────────────────────────────────────────────────

	public String exportPgn() {
		return pgnService.exportPgn(games);
	}
}
