package com.chess.analyzer.service;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.game.Game;
import com.github.bhlangonijr.chesslib.game.Player;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import com.github.bhlangonijr.chesslib.pgn.PgnHolder;

/**
 * Carrega arquivos PGN usando chesslib e os converte em {@link GameData}.
 *
 * <p>
 * Particularidade importante: quando a partida começa de uma posição
 * customizada ([FEN ...][SetUp "1"]), o chesslib 1.3.6 processa a tag FEN
 * internamente e NÃO a preserva em {@code game.getProperty()}. Por isso o FEN é
 * extraído diretamente do texto bruto do PGN via regex, antes de qualquer
 * chamada ao chesslib. Os lances são então parseados token a token, usando o
 * FEN corrente como contexto de cada chamada a {@code MoveList.loadFromSan()},
 * o que evita o NPE em {@code Board.isMoveLegal} causado por {@code Move}
 * objects resolvidos contra a posição errada.
 * </p>
 */
@Service
public class PgnService {

	private static final Logger log = LoggerFactory.getLogger(PgnService.class);

	private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/** Captura o valor da tag [FEN "..."] (case-insensitive). */
	private static final Pattern TAG_FEN_PATTERN = Pattern.compile("\\[FEN\\s+\"([^\"]+)\"\\]",
			Pattern.CASE_INSENSITIVE);

	// ── Carregamento ─────────────────────────────────────────────

	public List<GameData> load(MultipartFile file) throws Exception {
		String content = new String(file.getBytes(), StandardCharsets.UTF_8);
		return parseContent(content);
	}

	public List<GameData> load(File file) throws Exception {
		try (Reader r = new FileReader(file, StandardCharsets.UTF_8)) {
			return parseContent(readAll(r));
		}
	}

	private List<GameData> parseContent(String pgnContent) throws Exception {
		PgnHolder pgn = new PgnHolder("-");
		pgn.loadPgn(pgnContent);

		List<Game> games = pgn.getGames();
		if (games == null || games.isEmpty()) {
			log.warn("Arquivo PGN não contém partidas.");
			return List.of();
		}

		/*
		 * Extrai os FENs diretamente do texto bruto, indexados por posição de partida.
		 * O chesslib 1.3.6 NÃO preserva a tag FEN em game.getProperty(), por isso não
		 * confiamos nele para isso.
		 */
		List<String> rawFens = extractFensFromRawPgn(pgnContent);
		log.debug("FENs extraídos do PGN: {}", rawFens);

		List<GameData> result = new ArrayList<>(games.size());
		for (int i = 0; i < games.size(); i++) {
			try {
				String rawFen = (i < rawFens.size()) ? rawFens.get(i) : null;
				result.add(convertGame(i, games.get(i), pgn, rawFen));
			} catch (Exception ex) {
				log.error("Falha ao parsear partida {}: {}", i, ex.getMessage(), ex);
			}
		}
		log.info("Carregadas {} partida(s) do PGN.", result.size());
		return result;
	}

	/**
	 * Extrai a tag {@code [FEN "..."]} de cada partida no PGN bruto.
	 *
	 * <p>
	 * Divide o conteúdo em blocos de partida usando {@code [Event} como delimitador
	 * (lookahead, não consome o token). Para cada bloco, tenta encontrar a tag FEN.
	 * Retorna {@code null} para partidas sem ela.
	 * </p>
	 */
	private List<String> extractFensFromRawPgn(String pgnContent) {
		List<String> fens = new ArrayList<>();

		/*
		 * Divide em blocos independentes de partida. O lookahead (?=\[Event) garante
		 * que a tag [Event] não seja descartada. Funciona mesmo quando o arquivo começa
		 * diretamente com [Event].
		 */
		String[] blocks = pgnContent.split("(?=\\[Event\\s)");
		for (String block : blocks) {
			if (block.isBlank())
				continue;
			Matcher m = TAG_FEN_PATTERN.matcher(block);
			String fen = m.find() ? m.group(1).trim() : null;
			fens.add(fen);
			log.debug("Bloco PGN: FEN extraído = '{}'", fen);
		}
		return fens;
	}

	// ── Conversão de partida ─────────────────────────────────────

	/**
	 * Converte um {@link Game} do chesslib em {@link GameData}.
	 *
	 * @param rawFenFromPgn FEN extraído do texto bruto (pode ser null para posição
	 *                      padrão)
	 */
	private GameData convertGame(int index, Game game, PgnHolder pgn, String rawFenFromPgn) throws Exception {

		/*
		 * CRÍTICO: capturar o texto de lances ANTES de loadMoveText(). Após
		 * loadMoveText(), o chesslib pode zerar/consumir o moveText interno. O texto
		 * raw é usado como fonte para re-parsear os lances com o FEN correto.
		 */
		String rawMoveText = (game.getMoveText() != null) ? game.getMoveText().toString().trim() : null;

		game.loadMoveText();

		// ── Tags ──────────────────────────────────────────────────
		Map<String, String> tags = new LinkedHashMap<>();

		String evtName = extractPgnHolderValue(pgn.getEvent());
		String siteName = getPropertyValue(game, "Site");
		String round = getPropertyValue(game, "Round");
		String white = game.getWhitePlayer() != null ? game.getWhitePlayer().getName() : null;
		String black = game.getBlackPlayer() != null ? game.getBlackPlayer().getName() : null;
		String result = game.getResult() != null ? game.getResult().getDescription() : null;

		addTag(tags, "Event", evtName);
		addTag(tags, "Site", siteName);
		addTag(tags, "Date", game.getDate());
		addTag(tags, "Round", round);
		addTag(tags, "White", white);
		addTag(tags, "Black", black);
		addTag(tags, "Result", result);

		Player wp = game.getWhitePlayer();
		Player bp = game.getBlackPlayer();
		if (wp != null && wp.getElo() > 0)
			tags.put("WhiteElo", String.valueOf(wp.getElo()));
		if (bp != null && bp.getElo() > 0)
			tags.put("BlackElo", String.valueOf(bp.getElo()));

		if (game.getProperty() != null) {
			game.getProperty().forEach((k, v) -> {
				if (v != null && !v.isBlank())
					tags.putIfAbsent(k, v);
			});
		}

		// ── FEN inicial — três fontes em ordem de confiança ───────
		//
		// 1. rawFenFromPgn: extraído do texto PGN bruto via regex (mais
		// confiável — não depende do chesslib preservar getProperty()).
		// 2. tags.get("FEN"): via chesslib.getProperty() (raramente funciona
		// para FEN/SetUp no chesslib 1.3.6).
		// 3. START_FEN: posição padrão como último recurso.
		//
		String setupFen = rawFenFromPgn;
		if (isBlankOrUnknown(setupFen)) {
			setupFen = tags.get("FEN");
		}
		if (isBlankOrUnknown(setupFen)) {
			setupFen = START_FEN;
		}

		// Garante que o FEN customizado também esteja nas tags (para export)
		if (!START_FEN.equals(setupFen)) {
			tags.put("FEN", setupFen);
			tags.put("SetUp", "1");
		}

		log.debug("Partida {}: setupFen='{}'", index, setupFen);

		// ── MoveList com FEN correto ──────────────────────────────
		MoveList halfMoves = resolveHalfMoves(index, game, setupFen, rawMoveText);

		// ── Replay para gerar FENs e SANs ─────────────────────────
		List<MoveEntry> entries = new ArrayList<>(halfMoves.size());
		Board board = new Board();
		board.loadFromFen(setupFen);

		int ply = 0;
		for (Move move : halfMoves) {
			String fenBefore = board.getFen();
			boolean whiteTurn = fenBefore.split("\\s+")[1].equals("w");
			int moveNumber = (ply / 2) + 1;

			String uci = toUci(move);
			String san = toSan(board, move);

			boolean ok = board.doMove(move);
			if (!ok) {
				log.warn("Lance ilegal {} na partida {}, ply {}", uci, index, ply);
				break;
			}

			entries.add(new MoveEntry(uci, san, fenBefore, board.getFen(), moveNumber, whiteTurn));
			ply++;
		}

		return new GameData(index, tags, setupFen, entries);
	}

	// ── Resolução de MoveList ────────────────────────────────────

	/**
	 * Retorna uma {@link MoveList} com os {@link Move} objects resolvidos contra a
	 * FEN correta.
	 *
	 * <p>
	 * Para a posição padrão retorna {@code game.getHalfMoves()} diretamente (sem
	 * problema). Para FEN customizada, usa o parser token a token.
	 * </p>
	 */
	private MoveList resolveHalfMoves(int index, Game game, String setupFen, String rawMoveText) {
		if (START_FEN.equals(setupFen)) {
			return game.getHalfMoves();
		}

		log.debug("Partida {}: FEN customizada — usando parser token a token.", index);

		// Fonte 1: rawMoveText capturado ANTES de loadMoveText()
		if (!isBlankOrUnknown(rawMoveText)) {
			MoveList result = parseMovesTokenByToken(index, setupFen, stripAnnotations(rawMoveText));
			if (!result.isEmpty()) {
				log.debug("Partida {}: {} lances via rawMoveText.", index, result.size());
				return result;
			}
			log.warn("Partida {}: parser com rawMoveText não produziu lances.", index);
		} else {
			log.warn("Partida {}: rawMoveText nulo/vazio.", index);
		}

		// Fonte 2: toSan() dos halfMoves já carregados (strings SAN preservadas)
		try {
			String sanText = game.getHalfMoves().toSan();
			if (!isBlankOrUnknown(sanText)) {
				MoveList result = parseMovesTokenByToken(index, setupFen, stripAnnotations(sanText));
				if (!result.isEmpty()) {
					log.debug("Partida {}: {} lances via toSan().", index, result.size());
					return result;
				}
			}
		} catch (Exception e) {
			log.warn("Partida {}: toSan() falhou: {}", index, e.getMessage());
		}

		log.warn("Partida {}: todas as fontes falharam — usando halfMoves original (NPE provável).", index);
		return game.getHalfMoves();
	}

	/**
	 * Parseia lances SAN um token por vez, mantendo um {@link Board} auxiliar
	 * sincronizado.
	 *
	 * <p>
	 * Cada chamada a {@code new MoveList(aux.getFen())} cria um contexto limpo com
	 * o FEN <em>corrente</em> daquele ply. Isso evita o problema de desambiguação
	 * incorreta que ocorre quando se parseia a sequência inteira a partir do FEN
	 * inicial.
	 * </p>
	 */
	private MoveList parseMovesTokenByToken(int index, String setupFen, String cleanedSan) {
		MoveList result = new MoveList(setupFen);
		Board aux = new Board();
		aux.loadFromFen(setupFen);

		for (String token : cleanedSan.split("\\s+")) {
			if (token.isBlank())
				continue;
			try {
				MoveList single = new MoveList(aux.getFen());
				single.loadFromSan(token);
				if (single.isEmpty()) {
					log.warn("Partida {} token '{}': lista vazia — encerrando.", index, token);
					break;
				}
				Move move = single.get(0);
				result.add(move);
				boolean ok = aux.doMove(move);
				if (!ok) {
					log.warn("Partida {} token '{}': doMove=false — encerrando.", index, token);
					break;
				}
			} catch (Exception e) {
				log.warn("Partida {} token '{}': {} — encerrando.", index, token, e.getMessage());
				break;
			}
		}

		return result;
	}

	/**
	 * Remove comentários ({@code { }}), variantes ({@code ( )}), anotações NAG
	 * ({@code $N}), numeração de lances e token de resultado do texto PGN de
	 * lances. Preserva tokens SAN incluindo {@code O-O}, {@code +}, {@code #},
	 * {@code =Q}, etc.
	 */
	private String stripAnnotations(String moveText) {
		StringBuilder sb = new StringBuilder(moveText.length());
		int depth = 0;
		boolean inBrace = false;
		boolean inSemi = false;

		for (int i = 0; i < moveText.length(); i++) {
			char c = moveText.charAt(i);
			if (inSemi) {
				if (c == '\n')
					inSemi = false;
				continue;
			}
			if (inBrace) {
				if (c == '}')
					inBrace = false;
				continue;
			}
			if (c == '{') {
				inBrace = true;
				continue;
			}
			if (c == ';') {
				inSemi = true;
				continue;
			}
			if (c == '(') {
				depth++;
				continue;
			}
			if (c == ')') {
				if (depth > 0)
					depth--;
				continue;
			}
			if (depth == 0)
				sb.append(c);
		}

		return sb.toString().replaceAll("\\$\\d+", " ") // NAG
				.replaceAll("1-0|0-1|1/2-1/2|\\*", " ") // resultado
				.replaceAll("\\d+\\.+", " ") // numeração
				.replaceAll("\\s+", " ").trim();
	}

	// ── Exportação PGN ───────────────────────────────────────────

	/**
	 * Serializa as partidas (com análise) para PGN anotado. Avaliações são emitidas
	 * como comentários Lichess: {@code { [%eval +0.28] }}
	 */
	public String exportPgn(List<GameData> games) {
		StringBuilder sb = new StringBuilder();
		for (GameData g : games) {
			appendGamePgn(sb, g);
			sb.append("\n\n");
		}
		return sb.toString();
	}

	private void appendGamePgn(StringBuilder sb, GameData game) {
		for (Map.Entry<String, String> e : game.getTags().entrySet()) {
			sb.append("[%s \"%s\"]\n".formatted(e.getKey(), e.getValue()));
		}
		sb.append("\n");

		for (MoveEntry m : game.getMoves()) {
			if (m.isWhiteTurn())
				sb.append(m.getMoveNumber()).append(". ");
			sb.append(m.getSan() != null && !m.getSan().isBlank() ? m.getSan() : m.getUci());

			if (m.isAnalyzed()) {
				if (m.getMateIn() != null)
					sb.append(" { [%%eval #%d] }".formatted(m.getMateIn()));
				else if (m.getEval() != null)
					sb.append(" { [%%eval %+.2f] }".formatted(m.getEval()));
			}
			sb.append(" ");
		}

		sb.append(game.getTags().getOrDefault("Result", "*"));
	}

	// ── Helpers ──────────────────────────────────────────────────

	/** Converte {@link Move} para notação UCI ("e2e4", "e7e8q"). */
	private String toUci(Move move) {
		String from = move.getFrom().value().toLowerCase(Locale.ROOT);
		String to = move.getTo().value().toLowerCase(Locale.ROOT);
		String promo = "";
		if (move.getPromotion() != null && move.getPromotion() != Piece.NONE)
			promo = move.getPromotion().getFenSymbol().toLowerCase(Locale.ROOT);
		return from + to + promo;
	}

	/** Tenta obter a SAN do lance via chesslib. Retorna UCI como fallback. */
	private String toSan(Board board, Move move) {
		try {
			MoveList ml = new MoveList(board.getFen());
			ml.add(move);
			String[] arr = ml.toSanArray();
			if (arr != null && arr.length > 0 && arr[0] != null)
				return arr[0];
		} catch (Exception e) {
			log.debug("toSan: fallback UCI — {}", e.getMessage());
		}
		return toUci(move);
	}

	/**
	 * Extrai o valor da estrutura retornada por {@code PgnHolder.getEvent()}. O
	 * PgnHolder retorna um {@code Map<String, Object>} onde a chave é o nome do
	 * evento.
	 */
	private String extractPgnHolderValue(Object mapOrValue) {
		if (mapOrValue == null)
			return null;

		if (mapOrValue instanceof Map<?, ?> map) {
			if (map.isEmpty())
				return null;
			Object key = map.keySet().iterator().next();
			if (key != null)
				return key.toString();
			Object value = map.values().iterator().next();
			if (value != null) {
				try {
					return (String) value.getClass().getMethod("getName").invoke(value);
				} catch (Exception e) {
					return value.toString();
				}
			}
		}

		if (mapOrValue instanceof String s)
			return s;
		return mapOrValue.toString();
	}

	private String getPropertyValue(Game game, String key) {
		if (game.getProperty() != null) {
			Object v = game.getProperty().get(key);
			if (v != null)
				return v.toString();
		}
		return null;
	}

	private void addTag(Map<String, String> tags, String key, String value) {
		tags.put(key, value != null && !value.isBlank() ? value : "?");
	}

	private boolean isBlankOrUnknown(String s) {
		return s == null || s.isBlank() || "?".equals(s);
	}

	private String readAll(Reader r) throws IOException {
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[8192];
		int n;
		while ((n = r.read(buf)) != -1)
			sb.append(buf, 0, n);
		return sb.toString();
	}
}