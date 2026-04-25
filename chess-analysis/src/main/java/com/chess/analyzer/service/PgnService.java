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
 * Também exporta partidas analisadas de volta para PGN com comentários de
 * avaliação.
 */
@Service
public class PgnService {

	private static final Logger log = LoggerFactory.getLogger(PgnService.class);

	private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

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
		pgn.loadPgn(pgnContent); // ← direto String, sem StringReader

		List<Game> games = pgn.getGames();
		if (games == null || games.isEmpty()) {
			log.warn("Arquivo PGN não contém partidas.");
			return List.of();
		}

		List<GameData> result = new ArrayList<>(games.size());
		for (int i = 0; i < games.size(); i++) {
			try {
				result.add(convertGame(i, games.get(i), pgn));
			} catch (Exception ex) {
				log.error("Falha ao parsear partida {}: {}", i, ex.getMessage(), ex);
			}
		}
		log.info("Carregadas {} partida(s) do PGN.", result.size());
		return result;
	}

	private GameData convertGame(int index, Game game, PgnHolder pgn) throws Exception {
		game.loadMoveText();

		// ── Tags ────────────────────────────────────────────────
		//
		// Em chesslib, os campos do Seven-Tag Roster (Event, Site, Round, etc.)
		// são armazenados no PgnHolder, não no Game individual. game.getProperty()
		// retorna null quando carregado de String.
		//
		Map<String, String> tags = new LinkedHashMap<>();

		// Seven-Tag Roster — Event via PgnHolder, Site/Round via game.getProperty()
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

		// ELOs — chesslib armazena como int no Player; 0 = não informado
		Player wp = game.getWhitePlayer();
		Player bp = game.getBlackPlayer();
		if (wp != null && wp.getElo() > 0)
			tags.put("WhiteElo", String.valueOf(wp.getElo()));
		if (bp != null && bp.getElo() > 0)
			tags.put("BlackElo", String.valueOf(bp.getElo()));

		// Tags extras do cabeçalho (WhiteRatingDiff, BlackRatingDiff, Opening,
		// TimeControl, Termination, ECO, Variant, Annotator, GameId, etc.)
		// putIfAbsent garante que os Seven-Tag Roster acima não sejam sobrescritos.
		if (game.getProperty() != null) {
			game.getProperty().forEach((k, v) -> {
				if (v != null && !v.isBlank())
					tags.putIfAbsent(k, v);
			});
		}

		// ── FEN inicial ─────────────────────────────────────────
		String setupFen = tags.getOrDefault("FEN", START_FEN);
		if ("?".equals(setupFen) || setupFen.isBlank())
			setupFen = START_FEN;

		// ── Replay para gerar FENs e SANs ───────────────────────
		MoveList halfMoves = game.getHalfMoves();
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
			String fenAfter = board.getFen();

			entries.add(new MoveEntry(uci, san, fenBefore, fenAfter, moveNumber, whiteTurn));
			ply++;
		}

		return new GameData(index, tags, setupFen, entries);
	}

	// ── Exportação PGN ───────────────────────────────────────────

	/**
	 * Serializa as partidas (com análise) para PGN anotado. Avaliações são emitidas
	 * como comentários no formato Lichess: {@code { [%eval +0.28] }}
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
		// Cabeçalhos
		for (Map.Entry<String, String> e : game.getTags().entrySet()) {
			sb.append("[%s \"%s\"]\n".formatted(e.getKey(), e.getValue()));
		}
		sb.append("\n");

		List<MoveEntry> moves = game.getMoves();
		for (int i = 0; i < moves.size(); i++) {
			MoveEntry m = moves.get(i);

			if (m.isWhiteTurn()) {
				sb.append(m.getMoveNumber()).append(". ");
			}

			// Usa SAN se disponível, fallback para UCI
			sb.append(m.getSan() != null && !m.getSan().isBlank() ? m.getSan() : m.getUci());

			// Comentário de avaliação (formato Lichess)
			if (m.isAnalyzed()) {
				if (m.getMateIn() != null) {
					sb.append(" { [%%eval #%d] }".formatted(m.getMateIn()));
				} else if (m.getEval() != null) {
					sb.append(" { [%%eval %+.2f] }".formatted(m.getEval()));
				}
			}
			sb.append(" ");
		}

		sb.append(game.getTags().getOrDefault("Result", "*"));
	}

	// ── Helpers ──────────────────────────────────────────────────

	/** Converte Move para notação UCI ("e2e4", "e7e8q"). */
	private String toUci(Move move) {
		String from = move.getFrom().value().toLowerCase(Locale.ROOT);
		String to = move.getTo().value().toLowerCase(Locale.ROOT);
		String promo = "";
		if (move.getPromotion() != null && move.getPromotion() != Piece.NONE)
			promo = move.getPromotion().getFenSymbol().toLowerCase(Locale.ROOT);
		return from + to + promo;
	}

	/**
	 * Tenta obter a SAN do lance via chesslib. Retorna UCI como fallback.
	 */
	private String toSan(Board board, Move move) {
		try {
			MoveList ml = new MoveList(board.getFen());
			ml.add(move);
			String[] arr = ml.toSanArray();
			if (arr != null && arr.length > 0 && arr[0] != null)
				return arr[0];
		} catch (Exception e) {
			log.debug("toSan: fallback para UCI — {}", e.getMessage());
		}
		return toUci(move);
	}

	/**
	 * Extrai o valor da HashMap retornada por PgnHolder.getEvent(), etc.
	 * O PgnHolder retorna um Map<String, Object> onde a chave é o nome do evento
	 * e o valor é um objeto Event/Site/Round com método getName().
	 */
	private String extractPgnHolderValue(Object mapOrValue) {
		if (mapOrValue == null) {
			return null;
		}
		
		// Se for uma Map (como retorna pgn.getEvent()), extrair o primeiro valor
		if (mapOrValue instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) mapOrValue;
			if (map.isEmpty()) {
				return null;
			}
			
			// A chave em si é o valor (ex: Event name é a chave)
			Object key = map.keySet().iterator().next();
			if (key != null) {
				return key.toString();
			}
			
			// Fallback: tenta o valor do map
			Object value = map.values().iterator().next();
			if (value != null) {
				try {
					// Tenta chamar getName() se existir
					java.lang.reflect.Method getName = value.getClass().getMethod("getName");
					return (String) getName.invoke(value);
				} catch (Exception e) {
					return value.toString();
				}
			}
		}
		
		// Se for uma String simples, retornar como está
		if (mapOrValue instanceof String) {
			return (String) mapOrValue;
		}
		
		// Fallback: toString()
		return mapOrValue.toString();
	}

	private String getPropertyValue(Game game, String key) {
		if (game.getProperty() != null) {
			Object value = game.getProperty().get(key);
			if (value != null) {
				return value.toString();
			}
		}
		return null;
	}

	private void addTag(Map<String, String> tags, String key, String value) {
		tags.put(key, value != null && !value.isBlank() ? value : "?");
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
