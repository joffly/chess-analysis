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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;
import com.chess.analyzer.model.StockfishResult;
import com.chess.analyzer.util.LichessBlunderClassifier;
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
 *
 * <h3>Classificação de blunder</h3>
 * <p>Usamos o critério exato do Lichess: queda de <em>winning chances</em>
 * calculada via sigmoid sobre centipeões. Um lance é blunder quando essa
 * queda (na perspectiva do jogador) for &ge; 0.30 na escala [-1, +1].<br>
 * A avaliação <em>após</em> o lance jogado é obtida da análise do ply
 * seguinte (posição resultante), e então propagada de volta ao ply anterior
 * via {@link MoveEntry#setEvalAfter} antes de persistir.</p>
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

	// ── Gerenciamento de estado ────────────────────────────────────

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

	// ── SSE ────────────────────────────────────────────────────

	public SseEmitter createEmitter() {
		SseEmitter emitter = new SseEmitter(0L);
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));
		return emitter;
	}

	// ── Análise ──────────────────────────────────────────────────

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

	// ── Loop de análise ──────────────────────────────────────────────

	private void runAnalysis() throws Exception {
		cleanAnalysisContext();
		int totalPlies = games.stream().mapToInt(g -> g.getMoves().size()).sum();
		AtomicInteger analyzedCounter = new AtomicInteger(0);

		broadcast("START", Map.of(
				"totalGames", games.size(),
				"totalMoves", totalPlies,
				"depth", analysisDepth,
				"poolSize", stockfishPool.getPoolSize()));

		// Coleta todas as posições para análise em paralelo
		List<AnalysisTask> tasks = new ArrayList<>();
		for (GameData game : games) {
			List<MoveEntry> moves = game.getMoves();
			for (int mi = 0; mi < moves.size(); mi++) {
				tasks.add(new AnalysisTask(game, mi, moves.get(mi)));
			}
		}

		// Semáforo limitando concorrência ao tamanho do pool do Stockfish.
		// Evita criar centenas de virtual threads simultâneas que acumulam
		// objetos Board/análise na heap e causam OutOfMemoryError.
		final int poolSize = stockfishPool.getPoolSize();
		final Semaphore semaphore = new Semaphore(poolSize);

		// Fase 1: analisa todas as posições em paralelo
		try {
			List<CompletableFuture<Void>> futures = tasks.stream()
					.map(task -> {
						CompletableFuture<Void> future = new CompletableFuture<>();
						Thread.ofVirtual().start(() -> {
							try {
								semaphore.acquire();
								try {
									executeAnalysis(task, analyzedCounter, totalPlies);
								} finally {
									semaphore.release();
								}
								future.complete(null);
							} catch (Exception e) {
								log.error("Erro ao analisar lance: {}", e.getMessage());
								future.completeExceptionally(e);
							}
						});
						return future;
					})
					.toList();

			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		} catch (Exception e) {
			if (e.getCause() instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				log.warn("Análise interrompida.");
				return;
			}
			throw e;
		}

		// Fase 2: propaga evalAfter e calcula blunder (sequencial por partida)
		// Precisa ser feito DEPOIS que todos os plies foram analisados,
		// pois o evalAfter do lance i é o eval do lance i+1.
		for (GameData game : games) {
			propagateEvalAfterAndClassify(game);
		}

		games.forEach(game -> game.setFullyAnalyzed(true));
		games.forEach(game -> broadcast("GAME_DONE",
				Map.of("gameIndex", game.getIndex(), "title", game.getTitle())));
	}

	/**
	 * Para cada ply da partida, propaga a avaliação do ply seguinte como
	 * {@code evalAfter} do ply atual, e classifica o lance como blunder
	 * usando o critério Lichess.
	 *
	 * <p>A avaliação que o Stockfish retorna para o ply {@code i} é a melhor
	 * jogada possível em {@code fenBefore[i]}. Para saber o que aconteceu
	 * <em>após</em> o lance jogado, usamos a avaliação de {@code fenBefore[i+1]},
	 * que é exatamente {@code fenAfter[i]}.</p>
	 */
	private void propagateEvalAfterAndClassify(GameData game) {
		List<MoveEntry> moves = game.getMoves();
		for (int i = 0; i < moves.size(); i++) {
			MoveEntry current = moves.get(i);
			if (!current.isAnalyzed()) continue;

			// evalAfter do lance i = eval do lance i+1 (mesmo FEN)
			if (i + 1 < moves.size()) {
				MoveEntry next = moves.get(i + 1);
				if (next.isAnalyzed()) {
					current.setEvalAfter(next.getEval(), next.getMateIn());
				}
			}
			// Último lance: não há próximo ply; evalAfter permanece null
		}
	}

	/**
	 * Classifica um lance como blunder usando o critério Lichess.
	 *
	 * <p>Casos tratados:</p>
	 * <ol>
	 *   <li>Ambas avaliações são centipeões → usa sigmoid + threshold 0.30.</li>
	 *   <li>Antes era mate (jogador tinha mate) e depois não é mais
	 *       → MateLost: blunder (salvo posições quase ganhas).</li>
	 *   <li>Antes não era mate e depois o adversário tem mate
	 *       → MateCreated: blunder (salvo posições já perdidas).</li>
	 * </ol>
	 *
	 * @param move       lance analisado
	 * @return {@code true} se for blunder
	 */
	public static boolean isBlunder(MoveEntry move) {
		if (!move.isAnalyzed()) return false;

		Double cpBefore = move.getEval();
		Double cpAfter  = move.getEvalAfter();
		Integer mateBefore = move.getMateIn();
		Integer mateAfter  = move.getMateInAfter();

		// --- Caso 2: MateLost -----------------------------------------------
		// O jogador tinha mate forçado (mateBefore > 0 da perspectiva do jogador)
		// e depois do lance não existe mais mate positivo.
		boolean hadPosMate = (mateBefore != null) &&
				(move.isWhiteTurn() ? mateBefore > 0 : mateBefore < 0);
		boolean stillHasMate = (mateAfter != null) &&
				(move.isWhiteTurn() ? mateAfter < 0 : mateAfter > 0); // atenção: perspectiva invertida
		if (hadPosMate && !stillHasMate) {
			// cpAfter da perspectiva do jogador
			double afterPov = cpAfter != null
					? (move.isWhiteTurn() ? cpAfter : -cpAfter)
					: 0.0;
			return LichessBlunderClassifier.classifyMateLost(afterPov)
					== LichessBlunderClassifier.Judgement.BLUNDER;
		}

		// --- Caso 3: MateCreated -------------------------------------------
		// O adversário ganhou mate forçado que não existia antes.
		boolean oppHasMateNow = (mateAfter != null) &&
				(move.isWhiteTurn() ? mateAfter < 0 : mateAfter > 0);
		if (oppHasMateNow && mateBefore == null) {
			double beforePov = cpBefore != null
					? (move.isWhiteTurn() ? cpBefore : -cpBefore)
					: 0.0;
			return LichessBlunderClassifier.classifyMateCreated(beforePov)
					== LichessBlunderClassifier.Judgement.BLUNDER;
		}

		// --- Caso 1: ambas avaliações em centipeões -------------------------
		if (cpBefore == null || cpAfter == null) return false;
		// cpBefore e cpAfter estão sempre na perspectiva das brancas (convenção Stockfish)
		return LichessBlunderClassifier.isBlunder(
				cpBefore * 100, // converte peões → centipeões
				cpAfter  * 100,
				move.isWhiteTurn());
	}

	private record AnalysisTask(GameData game, int moveIndex, MoveEntry move) {}

	private void executeAnalysis(AnalysisTask task, AtomicInteger counter, int totalPlies)
			throws Exception {
		if (!analyzing.get()) return;

		GameData game = task.game();
		int mi = task.moveIndex();
		MoveEntry move = task.move();

		StockfishResult r = stockfishPool.analyze(move.getFenBefore(), analysisDepth);

		// setAnalysis grava eval/mateIn do fenBefore (melhor lance possível)
		move.setAnalysis(r.mateIn() != null ? 0.0 : r.eval(), r.mateIn(), r.bestMove(), r.pv());

		int analyzed = counter.incrementAndGet();

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("gameIndex", game.getIndex());
		payload.put("moveIndex", mi);
		payload.put("eval", r.mateIn() != null ? null : r.eval());
		payload.put("mateIn", r.mateIn());
		payload.put("bestMove", uciToSan(move.getFenBefore(), r.bestMove()));
		payload.put("pv", r.pv().stream().limit(5)
				.map(pvUci -> uciToSan(getFenForPv(move.getFenBefore(), r.pv(), pvUci), pvUci))
				.toList());
		payload.put("evalStr", move.getEvalFormatted());
		payload.put("progress", analyzed * 100 / Math.max(1, totalPlies));
		broadcast("MOVE_ANALYZED", payload);
	}

	private void cleanAnalysisContext() {
		games.stream().filter(GameData::isFullyAnalyzed).forEach(g -> g.setFullyAnalyzed(false));
		emitters.removeIf(emitter -> {
			try {
				emitter.send(SseEmitter.event().comment("ping"));
				return false;
			} catch (Exception e) {
				log.warn("Emissor inativo removido. Motivo: {}", e.getMessage());
				return true;
			}
		});
	}

	private static String uciToSan(String fen, String uci) {
		if (uci == null || uci.isBlank()) return null;

		Board board = new Board();
		board.loadFromFen(fen);

		Square from = Square.valueOf(uci.substring(0, 2).toUpperCase());
		Square to   = Square.valueOf(uci.substring(2, 4).toUpperCase());

		Piece piece = board.getPiece(from);
		// Guard: square vazia indica FEN inconsistente ou lance inválido do Stockfish.
		// Retorna a notação UCI bruta em vez de lançar NPE.
		if (piece == null || piece == Piece.NONE) {
			log.warn("uciToSan: square de origem {} está vazia para UCI '{}' no FEN '{}'", from, uci, fen);
			return uci;
		}
		PieceType pieceType = piece.getPieceType();
		if (pieceType == null) {
			log.warn("uciToSan: PieceType null para peça {} em {} (UCI '{}', FEN '{}')", piece, from, uci, fen);
			return uci;
		}

		String fromStr = uci.substring(0, 2).toLowerCase();
		String toStr   = uci.substring(2, 4).toLowerCase();

		if (pieceType == PieceType.KING && Math.abs(uci.charAt(2) - uci.charAt(0)) == 2)
			return uci.charAt(2) > uci.charAt(0) ? "O-O" : "O-O-O";

		boolean isCapture = board.getPiece(to) != Piece.NONE
				|| (pieceType == PieceType.PAWN && uci.charAt(0) != uci.charAt(2));

		StringBuilder san = new StringBuilder();
		if (pieceType == PieceType.PAWN) {
			if (isCapture) san.append(fromStr.charAt(0)).append('x');
			san.append(toStr);
			if (uci.length() == 5) san.append('=').append(Character.toUpperCase(uci.charAt(4)));
		} else {
			san.append(pieceType.getSanSymbol());
			List<Square> ambiguous = board.legalMoves().stream()
					.filter(m -> m.getTo() == to && m.getFrom() != from)
					.filter(m -> board.getPiece(m.getFrom()).getPieceType() == pieceType)
					.map(Move::getFrom).toList();
			if (!ambiguous.isEmpty()) {
				boolean sameFile = ambiguous.stream()
						.anyMatch(s -> s.toString().toLowerCase().charAt(0) == fromStr.charAt(0));
				boolean sameRank = ambiguous.stream()
						.anyMatch(s -> s.toString().toLowerCase().charAt(1) == fromStr.charAt(1));
				if (!sameFile)       san.append(fromStr.charAt(0));
				else if (!sameRank)  san.append(fromStr.charAt(1));
				else                 san.append(fromStr);
			}
			if (isCapture) san.append('x');
			san.append(toStr);
		}

		Piece promo = Piece.NONE;
		if (uci.length() == 5) {
			Side side = board.getSideToMove();
			promo = switch (uci.charAt(4)) {
				case 'q' -> side == Side.WHITE ? Piece.WHITE_QUEEN  : Piece.BLACK_QUEEN;
				case 'r' -> side == Side.WHITE ? Piece.WHITE_ROOK   : Piece.BLACK_ROOK;
				case 'b' -> side == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
				case 'n' -> side == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
				default  -> Piece.NONE;
			};
		}
		board.doMove(new Move(from, to, promo));
		if (board.isKingAttacked())
			san.append(board.legalMoves().isEmpty() ? '#' : '+');

		return san.toString();
	}

	private static String getFenForPv(String initialFen, List<String> pv, String targetUci) {
		Board board = new Board();
		board.loadFromFen(initialFen);
		for (String uci : pv) {
			if (uci.equals(targetUci)) return board.getFen();
			try {
				Square from = Square.valueOf(uci.substring(0, 2).toUpperCase());
				Square to   = Square.valueOf(uci.substring(2, 4).toUpperCase());
				Piece promo = Piece.NONE;
				if (uci.length() == 5) {
					Side side = board.getSideToMove();
					promo = switch (uci.charAt(4)) {
						case 'q' -> side == Side.WHITE ? Piece.WHITE_QUEEN  : Piece.BLACK_QUEEN;
						case 'r' -> side == Side.WHITE ? Piece.WHITE_ROOK   : Piece.BLACK_ROOK;
						case 'b' -> side == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
						case 'n' -> side == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
						default  -> Piece.NONE;
					};
				}
				board.doMove(new Move(from, to, promo));
			} catch (Exception e) {
				return board.getFen();
			}
		}
		return board.getFen();
	}

	// ── Broadcast SSE ──────────────────────────────────────────────

	private void broadcast(String eventName, Map<String, Object> data) {
		if (emitters.isEmpty()) return;
		String json = toJson(data);
		List<SseEmitter> dead = new ArrayList<>();
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name(eventName).data(json));
			} catch (IOException e) {
				dead.add(emitter);
			}
		}
		if (!dead.isEmpty()) emitters.removeAll(dead);
	}

	private String toJson(Map<String, Object> map) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (var e : map.entrySet()) {
			if (!first) sb.append(",");
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
					if (!lf) sb.append(",");
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

	// ── Export ────────────────────────────────────────────────────

	public String exportPgn() {
		return pgnService.exportPgn(games);
	}
}
