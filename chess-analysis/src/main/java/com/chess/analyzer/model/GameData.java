package com.chess.analyzer.model;

import java.util.List;
import java.util.Map;

/**
 * Representa uma partida completa carregada de um arquivo PGN.
 */
public class GameData {

	private final int index; // posição no arquivo PGN (0-based)
	private final Map<String, String> tags; // cabeçalhos Seven-Tag Roster + extras
	private final String initialFen; // FEN inicial (START ou SetUp)
	private final List<MoveEntry> moves; // lista de meio-lances

	private volatile boolean fullyAnalyzed;

	public GameData(int index, Map<String, String> tags, String initialFen, List<MoveEntry> moves) {
		this.index = index;
		this.tags = Map.copyOf(tags);
		this.initialFen = initialFen;
		this.moves = moves; // mutável — análise preenche MoveEntry
	}

	public int getIndex() {
		return index;
	}

	public Map<String, String> getTags() {
		return tags;
	}

	public String getInitialFen() {
		return initialFen;
	}

	public List<MoveEntry> getMoves() {
		return moves;
	}

	public boolean isFullyAnalyzed() {
		return fullyAnalyzed;
	}

	public void setFullyAnalyzed(boolean v) {
		fullyAnalyzed = v;
	}

	/** Título legível para exibição na lista lateral. */
	public String getTitle() {
		String w = tags.getOrDefault("White", "?");
		String b = tags.getOrDefault("Black", "?");
		String e = tags.getOrDefault("Event", "");
		String d = tags.getOrDefault("Date", "");
		return ("%s vs %s — %s %s".formatted(w, b, e, d)).strip();
	}

	// ── Summary DTO (sem os lances, usado na listagem) ──────────────────────
	//
	// Além do Seven-Tag Roster original, expõe os campos extras comuns do
	// Lichess/FIDE para que o frontend possa exibir ELOs, diff de rating,
	// abertura, controle de tempo, etc. sem precisar de uma segunda requisição.
	// O mapa completo de tags também é incluído para uso genérico.
	//
	public record Summary(int index, String title, int totalMoves, boolean analyzed,
			// Seven-Tag Roster
			String result, String white, String black, String date, String event,
			// Campos Lichess / extras (null quando ausentes no PGN)
			String whiteElo, String blackElo, String whiteRatingDiff, String blackRatingDiff, String site,
			String opening, String timeControl, String termination,
			// Mapa completo para acesso a qualquer outro tag
			Map<String, String> tags) {
	}

	public Summary toSummary() {
		return new Summary(index, getTitle(), moves.size(), fullyAnalyzed,
				// Seven-Tag Roster
				tags.getOrDefault("Result", "*"), tags.getOrDefault("White", "?"), tags.getOrDefault("Black", "?"),
				tags.getOrDefault("Date", "?"), tags.getOrDefault("Event", "?"),
				// Lichess extras — null quando o tag não existe no PGN
				tagOrNull("WhiteElo"), tagOrNull("BlackElo"), tagOrNull("WhiteRatingDiff"),
				tagOrNull("BlackRatingDiff"), tagOrNull("Site"), tagOrNull("Opening"), tagOrNull("TimeControl"),
				tagOrNull("Termination"), tags // já é imutável (Map.copyOf no construtor)
		);
	}

	/** Retorna o valor do tag ou {@code null} se ausente ou igual a "?". */
	private String tagOrNull(String key) {
		String v = tags.get(key);
		return (v == null || v.isBlank() || "?".equals(v)) ? null : v;
	}
}
