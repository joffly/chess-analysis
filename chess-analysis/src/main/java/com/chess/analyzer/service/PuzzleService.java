package com.chess.analyzer.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	public List<Map<String, Object>> listEcos() {
		Map<String, EcoInfo> map = new TreeMap<>();
		for (GameData g : analysisService.getGames()) {
			String eco = tagOrNull(g, "ECO");
			if (eco == null) continue;
			String opening = tagOrNull(g, "Opening");
			EcoInfo info = map.computeIfAbsent(eco, k -> new EcoInfo(k, opening));
			info.gameCount++;
			if (info.openingName == null && opening != null) info.openingName = opening;
			// Conta blunders já analisados
			info.blunderCount += countBlunders(g);
		}

		List<Map<String, Object>> list = new ArrayList<>(map.size());
		for (EcoInfo info : map.values()) {
			Map<String, Object> dto = new LinkedHashMap<>();
			dto.put("eco", info.eco);
			dto.put("opening", info.openingName != null ? info.openingName : "");
			dto.put("games", info.gameCount);
			dto.put("blunders", info.blunderCount);
			list.add(dto);
		}
		return list;
	}

	// ── Geração de puzzles ──────────────────────────────────────────

	/**
	 * Gera a lista de puzzles para um determinado ECO. Cada puzzle representa
	 * uma posição em que o jogador da vez cometeu um blunder; o usuário deverá
	 * encontrar um lance melhor a partir dessa mesma posição.
	 */
	public List<Map<String, Object>> getPuzzlesByEco(String eco) {
		if (eco == null || eco.isBlank()) return List.of();
		String wanted = eco.trim().toUpperCase();

		List<Map<String, Object>> puzzles = new ArrayList<>();
		int puzzleId = 0;

		for (GameData g : analysisService.getGames()) {
			String gEco = tagOrNull(g, "ECO");
			if (gEco == null || !gEco.equalsIgnoreCase(wanted)) continue;

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
						dto.put("white", g.getTags().getOrDefault("White", "?"));
						dto.put("black", g.getTags().getOrDefault("Black", "?"));
						dto.put("eco", gEco);
						dto.put("opening", g.getTags().getOrDefault("Opening", ""));
						dto.put("gameTitle", g.getTitle());
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
		int gameCount;
		int blunderCount;
		EcoInfo(String eco, String openingName) {
			this.eco = eco;
			this.openingName = openingName;
		}
	}
}
