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
	private static final Pattern TAG_FEN_PATTERN = Pattern.compile(
			"\\[FEN\\s+\"([^\"]+)\"\\]", Pattern.CASE_INSENSITIVE);

	/** Captura qualquer tag PGN genérica: [TagName "Value"]. */
	private static final Pattern TAG_GENERIC_PATTERN = Pattern.compile(
			"\\[([A-Za-z][A-Za-z0-9_]*)\\s+\"([^\"]*)\"");

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

		// Extrai todos os blocos brutos (tags + movetext) por índice de partida.
		List<String> rawBlocks = splitRawBlocks(pgnContent);
		log.debug("Blocos PGN extraídos: {}", rawBlocks.size());

		List<GameData> result = new ArrayList<>(games.size());
		for (int i = 0; i < games.size(); i++) {
			try {
				String block = (i < rawBlocks.size()) ? rawBlocks.get(i) : "";
				result.add(convertGame(i, games.get(i), pgn, block));
			} catch (Exception ex) {
				log.error("Falha ao parsear partida {}: {}", i, ex.getMessage(), ex);
			}
		}
		log.info("Carregadas {} partida(s) do PGN.", result.size());
		return result;
	}

	/**
	 * Divide o PGN bruto em blocos individuais de partida usando [Event como
	 * delimitador (lookahead — não descarta o token).
	 */
	private List<String> splitRawBlocks(String pgnContent) {
		List<String> blocks = new ArrayList<>();
		for (String b : pgnContent.split("(?=\\[Event\\s)")) {
			if (!b.isBlank())
				blocks.add(b);
		}
		return blocks;
	}

	/**
	 * Lê todas as tags PGN de um bloco bruto via regex.
	 * Retorna mapa case-preservado (chave original do PGN).
	 */
	private Map<String, String> extractRawTags(String block) {
		Map<String, String> map = new LinkedHashMap<>();
		Matcher m = TAG_GENERIC_PATTERN.matcher(block);
		while (m.find()) {
			String key = m.group(1);
			String val = m.group(2).trim();
			if (!val.isEmpty())
				map.put(key, val);
		}
		return map;
	}

	// ── Conversão de partida ─────────────────────────────────────

	/**
	 * Converte um {@link Game} do chesslib em {@link GameData}.
	 *
	 * @param rawBlock bloco PGN bruto correspondente a esta partida
	 */
	private GameData convertGame(int index, Game game, PgnHolder pgn, String rawBlock) throws Exception {

		/*
		 * CRÍTICO: capturar o texto de lances ANTES de loadMoveText(). Após
		 * loadMoveText(), o chesslib pode zerar/consumir o moveText interno.
		 */
		String rawMoveText = (game.getMoveText() != null) ? game.getMoveText().toString().trim() : null;

		game.loadMoveText();

		// ── Extrai TODAS as tags do bloco bruto (fonte mais confiável) ────
		Map<String, String> rawTags = extractRawTags(rawBlock);
		log.debug("Partida {}: tags brutas extraídas = {}", index, rawTags.keySet());

		// ── Monta mapa de tags final ──────────────────────────────────────
		Map<String, String> tags = new LinkedHashMap<>();

		// Seven-Tag Roster — tenta chesslib primeiro, fallback para rawTags
		String evtName = extractPgnHolderValue(pgn.getEvent());
		String siteName = coalesce(
				getPropertyValue(game, "Site"),
				rawTags.get("Site"));
		String round = coalesce(
				getPropertyValue(game, "Round"),
				rawTags.get("Round"));
		String white = game.getWhitePlayer() != null ? game.getWhitePlayer().getName() : rawTags.get("White");
		String black = game.getBlackPlayer() != null ? game.getBlackPlayer().getName() : rawTags.get("Black");
		String result = game.getResult() != null ? game.getResult().getDescription() : rawTags.get("Result");

		addTag(tags, "Event",  coalesce(evtName, rawTags.get("Event")));
		addTag(tags, "Site",   siteName);
		addTag(tags, "Date",   coalesce(game.getDate(), rawTags.get("Date")));
		addTag(tags, "Round",  round);
		addTag(tags, "White",  white);
		addTag(tags, "Black",  black);
		addTag(tags, "Result", result);

		// ELO — tenta chesslib depois rawTags
		Player wp = game.getWhitePlayer();
		Player bp = game.getBlackPlayer();
		if (wp != null && wp.getElo() > 0)
			tags.put("WhiteElo", String.valueOf(wp.getElo()));
		else if (rawTags.containsKey("WhiteElo"))
			tags.put("WhiteElo", rawTags.get("WhiteElo"));

		if (bp != null && bp.getElo() > 0)
			tags.put("BlackElo", String.valueOf(bp.getElo()));
		else if (rawTags.containsKey("BlackElo"))
			tags.put("BlackElo", rawTags.get("BlackElo"));

		// Tags opcionais — extraídas explicitamente do bloco bruto
		// (o chesslib frequentemente não as preserva em getProperty())
		for (String key : List.of(
				"Opening", "ECO", "TimeControl", "Termination",
				"Variant", "WhiteRatingDiff", "BlackRatingDiff",
				"UTCDate", "UTCTime", "WhiteTitle", "BlackTitle")) {
			String val = coalesce(getPropertyValue(game, key), rawTags.get(key));
			if (!isBlankOrUnknown(val))
				tags.put(key, val);
		}

		// Demais tags de getProperty() não cobertas acima
		if (game.getProperty() != null) {
			game.getProperty().forEach((k, v) -> {
				if (v != null && !v.toString().isBlank())
					tags.putIfAbsent(k, v.toString());
			});
		}
		// Demais tags brutas não cobertas acima
		rawTags.forEach((k, v) -> {
			if (!isBlankOrUnknown(v))
				tags.putIfAbsent(k, v);
		});

		// ── FEN inicial ───────────────────────────────────────────────────
		String rawFenFromPgn = rawTags.get("FEN");
		String setupFen = rawFenFromPgn;
		if (isBlankOrUnknown(setupFen))
			setupFen = tags.get("FEN");
		if (isBlankOrUnknown(setupFen))
			setupFen = START_FEN;

		if (!START_FEN.equals(setupFen)) {
			tags.put("FEN",   setupFen);
			tags.put("SetUp", "1");
		}

		log.debug("Partida {}: setupFen='{}'", index, setupFen);

		// ── MoveList com FEN correto ──────────────────────────────────────
		MoveList halfMoves = resolveHalfMoves(index, game, setupFen, rawMoveText);

		// ── Replay para gerar FENs e SANs ─────────────────────────────────
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

	private MoveList resolveHalfMoves(int index, Game game, String setupFen, String rawMoveText) {
		if (START_FEN.equals(setupFen)) {
			return game.getHalfMoves();
		}

		log.debug("Partida {}: FEN customizada — usando parser token a token.", index);

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

		log.warn("Partida {}: todas as fontes falharam — usando halfMoves original.", index);
		return game.getHalfMoves();
	}

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

	private String stripAnnotations(String moveText) {
		StringBuilder sb = new StringBuilder(moveText.length());
		int depth = 0;
		boolean inBrace = false;
		boolean inSemi  = false;

		for (int i = 0; i < moveText.length(); i++) {
			char c = moveText.charAt(i);
			if (inSemi)  { if (c == '\n') inSemi  = false; continue; }
			if (inBrace) { if (c == '}')  inBrace = false; continue; }
			if (c == '{')  { inBrace = true;  continue; }
			if (c == ';')  { inSemi  = true;  continue; }
			if (c == '(')  { depth++;         continue; }
			if (c == ')') { if (depth > 0) depth--; continue; }
			if (depth == 0) sb.append(c);
		}

		return sb.toString()
				.replaceAll("\\$\\d+", " ")
				.replaceAll("1-0|0-1|1/2-1/2|\\*", " ")
				.replaceAll("\\d+\\.+", " ")
				.replaceAll("\\s+", " ").trim();
	}

	// ── Exportação PGN ───────────────────────────────────────────

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

	private String toUci(Move move) {
		String from  = move.getFrom().value().toLowerCase(Locale.ROOT);
		String to    = move.getTo().value().toLowerCase(Locale.ROOT);
		String promo = "";
		if (move.getPromotion() != null && move.getPromotion() != Piece.NONE)
			promo = move.getPromotion().getFenSymbol().toLowerCase(Locale.ROOT);
		return from + to + promo;
	}

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

	private String extractPgnHolderValue(Object mapOrValue) {
		if (mapOrValue == null) return null;
		if (mapOrValue instanceof Map<?, ?> map) {
			if (map.isEmpty()) return null;
			Object key = map.keySet().iterator().next();
			if (key != null) return key.toString();
			Object value = map.values().iterator().next();
			if (value != null) {
				try { return (String) value.getClass().getMethod("getName").invoke(value); }
				catch (Exception e) { return value.toString(); }
			}
		}
		if (mapOrValue instanceof String s) return s;
		return mapOrValue.toString();
	}

	private String getPropertyValue(Game game, String key) {
		if (game.getProperty() == null) return null;
		Object v = game.getProperty().get(key);
		return v != null ? v.toString() : null;
	}

	/** Retorna o primeiro valor não nulo/vazio/desconhecido da lista. */
	private String coalesce(String... values) {
		for (String v : values)
			if (!isBlankOrUnknown(v)) return v;
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
