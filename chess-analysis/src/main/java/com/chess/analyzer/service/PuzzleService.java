package com.chess.analyzer.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chess.analyzer.model.GameData;
import com.chess.analyzer.model.MoveEntry;

/**
 * Serviço responsável por gerar problemas (puzzles) de xadrez a partir do
 * "banco" em memória de partidas analisadas.
 *
 * Definição de blunder usada aqui (mesma adotada pela maioria dos sites como
 * Lichess): a avaliação Stockfish da posição cai significativamente do ponto
 * de vista do jogador que executou o lance. Limite default: queda &gt;= 1.5
 * peões.
 */
@Service
public class PuzzleService {

	private static final Logger log = LoggerFactory.getLogger(PuzzleService.class);

	/** Queda mínima de avaliação (em peões) para classificar um lance como blunder. */
	public static final double BLUNDER_THRESHOLD = 1.5;

	private final GameAnalysisService analysisService;

	public PuzzleService(GameAnalysisService analysisService) {
		this.analysisService = analysisService;
	}

	// ── ECOs disponíveis ────────────────────────────────────────────

	/**
	 * Retorna a lista de ECOs presentes nas partidas carregadas, agregando
	 * a quantidade de partidas e o nome da abertura (quando disponível).
	 * Ordenado alfabeticamente por código ECO.
	 */
	/** Delega para {@link #listEcos(String)} sem filtro de jogador. */
	public List<Map<String, Object>> listEcos() {
		return listEcos(null);
	}

	/**
	 * Lista ECOs com blunders, filtrando opcionalmente pelo jogador que estava
	 * na vez e cometeu o erro (case-insensitive, substring).
	 */
	public List<Map<String, Object>> listEcos(String playerFilter) {
		boolean hasFilter   = playerFilter != null && !playerFilter.isBlank();
		String  lowerFilter = hasFilter ? playerFilter.strip().toLowerCase() : null;

		Map<String, EcoInfo> map = new TreeMap<>();

		for (GameData g : analysisService.getGames()) {
			String eco = tagOrNull(g, "ECO");
			if (eco == null) continue;
			String opening = tagOrNull(g, "Opening");
			String white   = nvl(g.getTags().get("White"));
			String black   = nvl(g.getTags().get("Black"));

			double  prevEval = 0.0;
			boolean prevHas  = true;

			for (MoveEntry m : g.getMoves()) {
				if (!m.isAnalyzed()) { prevHas = false; continue; }
				Integer mate = m.getMateIn();
				Double  e    = m.getEval();
				double  cur;
				if      (mate != null) cur = mate > 0 ? 100.0 : -100.0;
				else if (e    != null) cur = e;
				else { prevHas = false; continue; }

				if (prevHas) {
					double drop = m.isWhiteTurn() ? prevEval - cur : cur - prevEval;
					if (drop >= BLUNDER_THRESHOLD) {
						String blunderMaker = m.isWhiteTurn() ? white : black;

						// Com filtro ativo: só conta blunders do jogador pesquisado
						if (hasFilter && !blunderMaker.toLowerCase().contains(lowerFilter)) {
							prevEval = cur; prevHas = true; continue;
						}

						EcoInfo info = map.computeIfAbsent(eco, k -> new EcoInfo(k, opening));
						if (info.openingName == null && opening != null) info.openingName = opening;
						info.blunderCount++;
						info.gameIndices.add(g.getIndex()); // deduplicado por Set

						if (!blunderMaker.isEmpty()) {
							PlayerInfoAgg pa = info.playerStats.computeIfAbsent(
									blunderMaker, k -> new PlayerInfoAgg());
							pa.blundersMade++;
							pa.gameIndices.add(g.getIndex());
						}
					}
				}
				prevEval = cur;
				prevHas  = true;
			}
		}

		List<Map<String, Object>> list = new ArrayList<>(map.size());
		for (EcoInfo info : map.values()) {
			Map<String, Object> dto = new LinkedHashMap<>();
			dto.put("eco",      info.eco);
			dto.put("opening",  info.openingName != null ? info.openingName : "");
			dto.put("games",    info.gameIndices.size()); // partidas únicas com blunder(s) relevantes
			dto.put("blunders", info.blunderCount);

			List<Map<String, Object>> playerList = new ArrayList<>();
			for (Map.Entry<String, PlayerInfoAgg> entry : info.playerStats.entrySet()) {
				Map<String, Object> ps = new LinkedHashMap<>();
				ps.put("name",     entry.getKey());
				ps.put("games",    entry.getValue().gameIndices.size());
				ps.put("blunders", entry.getValue().blundersMade);
				playerList.add(ps);
			}
			dto.put("playerStats", playerList);
			list.add(dto);
		}
		return list;
	}

	// ── Geração de puzzles ──────────────────────────────────────────

	/**
	 * Gera a lista de puzzles para um determinado ECO.
	 * Delega para {@link #getPuzzlesByEco(String, String)} sem filtro de jogador.
	 */
	public List<Map<String, Object>> getPuzzlesByEco(String eco) {
		return getPuzzlesByEco(eco, null);
	}

	/**
	 * Gera a lista de puzzles para um determinado ECO, filtrando opcionalmente pelo
	 * jogador que estava na vez e cometeu o blunder.
	 *
	 * @param eco          código ECO (ex: "B20"), case-insensitive
	 * @param playerFilter filtro pelo nome do jogador (case-insensitive, substring);
	 *                     {@code null} = sem filtro
	 */
	public List<Map<String, Object>> getPuzzlesByEco(String eco, String playerFilter) {
		if (eco == null || eco.isBlank()) return List.of();
		String wanted = eco.trim().toUpperCase();

		boolean hasFilter   = playerFilter != null && !playerFilter.isBlank();
		String  lowerFilter = hasFilter ? playerFilter.strip().toLowerCase() : null;

		List<Map<String, Object>> puzzles = new ArrayList<>();
		int puzzleId = 0;

		for (GameData g : analysisService.getGames()) {
			String gEco = tagOrNull(g, "ECO");
			if (gEco == null || !gEco.equalsIgnoreCase(wanted)) continue;

			String white = nvl(g.getTags().get("White"));
			String black = nvl(g.getTags().get("Black"));

			List<MoveEntry> moves = g.getMoves();
			double prevEval = 0.0;       // eval antes do primeiro lance (perspectiva brancas)
			boolean prevHasEval = true;

			for (int i = 0; i < moves.size(); i++) {
				MoveEntry m = moves.get(i);
				if (!m.isAnalyzed()) {
					// Sem análise → não dá pra detectar blunder aqui
					prevHasEval = false;
					continue;
				}

				Double curEvalObj = m.getEval();
				Integer mateIn = m.getMateIn();

				// Avaliação numérica desta posição (após o lance), perspectiva brancas
				double curEval;
				if (mateIn != null) {
					curEval = mateIn > 0 ? 100.0 : -100.0;
				} else if (curEvalObj != null) {
					curEval = curEvalObj;
				} else {
					prevHasEval = false;
					continue;
				}

				if (prevHasEval) {
					double drop;
					if (m.isWhiteTurn()) {
						// Brancas jogaram: blunder se eval caiu (de positiva pra negativa)
						drop = prevEval - curEval;
					} else {
						// Pretas jogaram: blunder se eval subiu (a favor das brancas)
						drop = curEval - prevEval;
					}

					if (drop >= BLUNDER_THRESHOLD) {
						// Aplica filtro por jogador: só inclui blunders do jogador pesquisado
						if (hasFilter) {
							String blunderMaker = m.isWhiteTurn() ? white : black;
							if (!blunderMaker.toLowerCase().contains(lowerFilter)) {
								prevEval = curEval;
								prevHasEval = true;
								continue;
							}
						}

						Map<String, Object> dto = new LinkedHashMap<>();
						dto.put("id", puzzleId++);
						dto.put("gameIndex", g.getIndex());
						dto.put("moveIndex", i);
						dto.put("fen", m.getFenBefore());           // posição p/ resolver
						dto.put("sideToMove", fenSide(m.getFenBefore()));
						dto.put("playedUci", m.getUci());
						dto.put("playedSan", m.getSan());
						dto.put("bestMove", m.getBestMove());        // melhor lance (UCI)
						dto.put("evalBefore", round(prevEval));
						dto.put("evalAfter",  mateIn != null ? null : round(curEval));
						dto.put("mateInAfter", mateIn);
						dto.put("drop", round(drop));
						dto.put("moveNumber", m.getMoveNumber());
						dto.put("white",    g.getTags().getOrDefault("White",   "?"));
						dto.put("black",    g.getTags().getOrDefault("Black",   "?"));
						dto.put("eco",      gEco);
						dto.put("opening",  g.getTags().getOrDefault("Opening", ""));
						dto.put("gameTitle",g.getTitle());
						dto.put("date",     nvl(g.getTags().get("Date")));
						dto.put("whiteElo", nvl(g.getTags().get("WhiteElo")));
						dto.put("blackElo", nvl(g.getTags().get("BlackElo")));
						dto.put("site",     nvl(g.getTags().get("Site")));
						puzzles.add(dto);
					}
				}

				prevEval = curEval;
				prevHasEval = true;
			}
		}

		log.info("Gerados {} puzzle(s) para ECO {}", puzzles.size(), wanted);
		return puzzles;
	}

	// ── Avaliação do lance do usuário ──────────────────────────────

	/**
	 * Classifica o lance do usuário com base nos parâmetros de avaliação:
	 *   - delta (perspectiva do jogador) é a queda da avaliação após o lance.
	 *
	 *   delta &lt;= 0.20  → "Excelente"
	 *   delta &lt;= 0.50  → "Bom"
	 *   delta &lt;= 1.00  → "Imprecisão"
	 *   delta &lt;= 2.00  → "Erro"
	 *   delta &gt; 2.00  → "Blunder"
	 *
	 *   evalBefore / evalAfter são fornecidos do ponto de vista das brancas.
	 *   Convertem-se para a perspectiva do lado que jogou.
	 */
	public Map<String, Object> classifyMove(double evalBefore, Double evalAfter, Integer mateInAfter,
			boolean whiteToMove, boolean userMoveEqualsBest) {

		Map<String, Object> result = new LinkedHashMap<>();

		// Eval pós-lance, perspectiva do jogador
		double playerEvalAfter;
		if (mateInAfter != null) {
			// Mate detectado após o lance
			boolean goodForUser = whiteToMove ? mateInAfter > 0 : mateInAfter < 0;
			playerEvalAfter = goodForUser ? 100.0 : -100.0;
		} else if (evalAfter != null) {
			playerEvalAfter = whiteToMove ? evalAfter : -evalAfter;
		} else {
			playerEvalAfter = 0.0;
		}

		double playerEvalBefore = whiteToMove ? evalBefore : -evalBefore;
		double delta = playerEvalBefore - playerEvalAfter;

		String classification;
		String color;
		String message;
		if (userMoveEqualsBest) {
			classification = "Lance perfeito";
			color = "good";
			message = "Você achou o melhor lance segundo o Stockfish!";
		} else if (delta <= 0.20) {
			classification = "Excelente";
			color = "good";
			message = "Lance praticamente igual ao melhor.";
		} else if (delta <= 0.50) {
			classification = "Bom";
			color = "good";
			message = "Bom lance — só um pouquinho abaixo do melhor.";
		} else if (delta <= 1.00) {
			classification = "Imprecisão";
			color = "warn";
			message = "Imprecisão: havia algo um pouco melhor.";
		} else if (delta <= 2.00) {
			classification = "Erro";
			color = "bad";
			message = "Erro: existia um lance bem melhor.";
		} else {
			classification = "Blunder";
			color = "bad";
			message = "Blunder! Esse lance compromete bastante a posição.";
		}

		result.put("classification", classification);
		result.put("color", color);
		result.put("message", message);
		result.put("evalBefore", round(evalBefore));
		result.put("evalAfter",  mateInAfter != null ? null : round(evalAfter == null ? 0.0 : evalAfter));
		result.put("mateInAfter", mateInAfter);
		result.put("delta", round(delta));
		result.put("isBest", userMoveEqualsBest);
		return result;
	}

	// ── Helpers ────────────────────────────────────────────────────

	private int countBlunders(GameData g) {
		int count = 0;
		double prevEval = 0.0;
		boolean prevHas = true;
		for (MoveEntry m : g.getMoves()) {
			if (!m.isAnalyzed()) { prevHas = false; continue; }
			Integer mate = m.getMateIn();
			Double e = m.getEval();
			double cur;
			if (mate != null) cur = mate > 0 ? 100.0 : -100.0;
			else if (e != null) cur = e;
			else { prevHas = false; continue; }

			if (prevHas) {
				double drop = m.isWhiteTurn() ? prevEval - cur : cur - prevEval;
				if (drop >= BLUNDER_THRESHOLD) count++;
			}
			prevEval = cur;
			prevHas = true;
		}
		return count;
	}

	private static String tagOrNull(GameData g, String key) {
		String v = g.getTags().get(key);
		return (v == null || v.isBlank() || "?".equals(v)) ? null : v;
	}

	private static String fenSide(String fen) {
		if (fen == null) return "w";
		String[] parts = fen.split("\\s+");
		return parts.length > 1 ? parts[1] : "w";
	}

	private static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	/** Auxiliar interno para agrupar contagem por ECO. */
	private static class EcoInfo {
		final String eco;
		String openingName;
		/** Índices únicos de partidas que têm ao menos um blunder relevante neste ECO. */
		final Set<Integer> gameIndices = new LinkedHashSet<>();
		int blunderCount;
		/** Estatísticas por jogador para este ECO. */
		final Map<String, PlayerInfoAgg> playerStats = new LinkedHashMap<>();

		EcoInfo(String eco, String openingName) {
			this.eco = eco;
			this.openingName = openingName;
		}
	}

	/** Agrega, por jogador, índices de partidas e blunders cometidos. */
	private static class PlayerInfoAgg {
		final Set<Integer> gameIndices = new LinkedHashSet<>();
		int blundersMade;
	}

	/** Retorna string vazia para valores nulos, em branco ou "?". */
	private static String nvl(String v) {
		return (v == null || v.isBlank() || "?".equals(v.trim())) ? "" : v.trim();
	}
}
