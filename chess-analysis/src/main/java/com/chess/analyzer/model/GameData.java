package com.chess.analyzer.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Representa uma partida completa carregada de um arquivo PGN.
 */
public class GameData {

	private final int index;               // posição no arquivo PGN (0-based)
	private final Map<String, String> tags; // cabeçalhos Seven-Tag Roster + extras
	private final String initialFen;       // FEN inicial (START ou SetUp)
	private final List<MoveEntry> moves;   // lista de meio-lances

	private volatile boolean fullyAnalyzed;

	public GameData(int index, Map<String, String> tags, String initialFen, List<MoveEntry> moves) {
		this.index = index;
		this.tags = Map.copyOf(tags);
		this.initialFen = initialFen;
		this.moves = moves;
	}

	public int getIndex()                  { return index; }
	public Map<String, String> getTags()   { return tags; }
	public String getInitialFen()          { return initialFen; }
	public List<MoveEntry> getMoves()      { return moves; }
	public boolean isFullyAnalyzed()       { return fullyAnalyzed; }
	public void setFullyAnalyzed(boolean v){ fullyAnalyzed = v; }

	/** Título legível para exibição na lista lateral. */
	public String getTitle() {
		String w = tags.getOrDefault("White", "?");
		String b = tags.getOrDefault("Black", "?");
		String e = tags.getOrDefault("Event", "");
		String d = tags.getOrDefault("Date",  "");
		return ("%s vs %s — %s %s".formatted(w, b, e, d)).strip();
	}

	/**
	 * Identificador estável da partida.
	 *
	 * <p><b>Fonte 1 — tag nativa do PGN</b>: se o arquivo contiver
	 * {@code [GameId "myF6FTAy"]}, esse valor é usado diretamente. É o caso
	 * do Lichess, chess.com e qualquer exportador que já inclua um ID
	 * externo e imutável.</p>
	 *
	 * <p><b>Fonte 2 — fallback SHA-256</b>: para PGNs sem a tag GameId,
	 * calcula um SHA-256 hex (64 chars) sobre a concatenação dos 7 campos
	 * do Seven-Tag Roster mais o FEN inicial, separados por {@code |}:
	 * <pre>White|Black|Event|Site|Date|Round|Result|initialFen</pre>
	 * Campos ausentes são normalizados para {@code "?"} para estabilizar
	 * o hash independentemente de como o PGN foi exportado.</p>
	 */
	public String getGameId() {
		// Fonte 1: tag nativa do PGN (Lichess: "myF6FTAy", etc.)
		String nativeId = tags.get("GameId");
		if (nativeId != null && !nativeId.isBlank() && !"?".equals(nativeId.trim())) {
			return nativeId.trim();
		}

		// Fonte 2: hash determinístico dos campos identitários
		String raw = String.join("|",
				nvl(tags.get("White")),
				nvl(tags.get("Black")),
				nvl(tags.get("Event")),
				nvl(tags.get("Site")),
				nvl(tags.get("Date")),
				nvl(tags.get("Round")),
				nvl(tags.get("Result")),
				nvl(initialFen)
		);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 não disponível", e);
		}
	}

	// ── Summary DTO (sem os lances, usado na listagem) ──────────────────────
	public record Summary(int index, String title, int totalMoves, boolean analyzed,
			String result, String white, String black, String date, String event,
			String whiteElo, String blackElo, String whiteRatingDiff, String blackRatingDiff, String site,
			String opening, String timeControl, String termination,
			Map<String, String> tags) {}

	public Summary toSummary() {
		return new Summary(index, getTitle(), moves.size(), fullyAnalyzed,
				tags.getOrDefault("Result", "*"),
				tags.getOrDefault("White",  "?"),
				tags.getOrDefault("Black",  "?"),
				tags.getOrDefault("Date",   "?"),
				tags.getOrDefault("Event",  "?"),
				tagOrNull("WhiteElo"),       tagOrNull("BlackElo"),
				tagOrNull("WhiteRatingDiff"), tagOrNull("BlackRatingDiff"),
				tagOrNull("Site"),           tagOrNull("Opening"),
				tagOrNull("TimeControl"),    tagOrNull("Termination"),
				tags);
	}

	private String tagOrNull(String key) {
		String v = tags.get(key);
		return (v == null || v.isBlank() || "?".equals(v)) ? null : v;
	}

	private static String nvl(String v) {
		return (v == null || v.isBlank() || "?".equals(v)) ? "?" : v;
	}
}
