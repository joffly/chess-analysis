package com.chess.analyzer.service;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
        // Pré-extrai textos de lances antes do PgnHolder consumir o conteúdo
        Map<Integer, String> rawMoveTexts = extractRawMoveTexts(pgnContent);

        PgnHolder pgn = new PgnHolder("-");
        pgn.loadPgn(pgnContent);

        List<Game> games = pgn.getGames();
        if (games == null || games.isEmpty()) {
            log.warn("Arquivo PGN não contém partidas.");
            return List.of();
        }

        List<GameData> result = new ArrayList<>(games.size());
        for (int i = 0; i < games.size(); i++) {
            try {
                result.add(convertGame(i, games.get(i), pgn, rawMoveTexts.get(i)));
            } catch (Exception ex) {
                log.error("Falha ao parsear partida {}: {}", i, ex.getMessage(), ex);
            }
        }

        log.info("Carregadas {} partida(s) do PGN.", result.size());
        return result;
    }

    /**
     * Extrai o texto de lances de cada partida diretamente do conteúdo PGN bruto,
     * antes de qualquer processamento pelo PgnHolder. Isso garante disponibilidade
     * mesmo em partidas [Variant "From Position"] onde game.getMoveText() retorna
     * null antes de loadMoveText().
     */
    private Map<Integer, String> extractRawMoveTexts(String pgnContent) {
        Map<Integer, String> map = new LinkedHashMap<>();
        // Divide em blocos por linha em branco seguida de '[' (início de nova partida)
        // ou pelo padrão de separação padrão PGN
        String[] blocks = pgnContent.split("\n\\s*\n(?=\\[)");
        int gameIndex = 0;
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;

            // Encontra o fim dos headers: última ocorrência de ']'
            int lastBracket = block.lastIndexOf(']');
            if (lastBracket < 0) continue;

            String moves = block.substring(lastBracket + 1).trim();
            if (!moves.isEmpty()) {
                map.put(gameIndex, moves);
            }
            gameIndex++;
        }
        return map;
    }

    private GameData convertGame(int index, Game game, PgnHolder pgn, String rawMoveText) throws Exception {

        // rawMoveText já foi capturado do PGN bruto em parseContent — não depende de
        // game.getMoveText() que pode ser null em partidas From Position (Lichess)
        game.loadMoveText();

        // ── Tags ──────────────────────────────────────────────────
        Map<String, String> tags = new LinkedHashMap<>();
        String evtName  = extractPgnHolderValue(pgn.getEvent());
        String siteName = getPropertyValue(game, "Site");
        String round    = getPropertyValue(game, "Round");
        String white    = game.getWhitePlayer() != null ? game.getWhitePlayer().getName() : null;
        String black    = game.getBlackPlayer() != null ? game.getBlackPlayer().getName() : null;
        String result   = game.getResult()      != null ? game.getResult().getDescription() : null;

        addTag(tags, "Event",  evtName);
        addTag(tags, "Site",   siteName);
        addTag(tags, "Date",   game.getDate());
        addTag(tags, "Round",  round);
        addTag(tags, "White",  white);
        addTag(tags, "Black",  black);
        addTag(tags, "Result", result);

        Player wp = game.getWhitePlayer();
        Player bp = game.getBlackPlayer();
        if (wp != null && wp.getElo() > 0) tags.put("WhiteElo", String.valueOf(wp.getElo()));
        if (bp != null && bp.getElo() > 0) tags.put("BlackElo", String.valueOf(bp.getElo()));
        if (game.getProperty() != null) {
            game.getProperty().forEach((k, v) -> {
                if (v != null && !v.isBlank()) tags.putIfAbsent(k, v);
            });
        }

        // ── FEN inicial ───────────────────────────────────────────
        String setupFen = tags.get("FEN");
        if (setupFen == null || setupFen.isBlank() || "?".equals(setupFen)) {
            setupFen = START_FEN;
        }

        // ── MoveList com FEN correta ──────────────────────────────
        // FIX: resolveHalfMoves retorna null quando todas as tentativas falham,
        // em vez de retornar os Move objects incompatíveis do game.getHalfMoves().
        MoveList halfMoves = resolveHalfMoves(index, game, setupFen, rawMoveText);
        if (halfMoves == null) {
            log.error("Partida {}: impossível resolver lances para FEN='{}'. Partida ignorada.", index, setupFen);
            return new GameData(index, tags, setupFen, List.of());
        }

        // ── Replay ────────────────────────────────────────────────
        List<MoveEntry> entries = new ArrayList<>(halfMoves.size());
        Board board = new Board();
        board.loadFromFen(setupFen);

        int ply = 0;
        for (Move move : halfMoves) {
            String  fenBefore  = board.getFen();
            boolean whiteTurn  = fenBefore.split("\\s+")[1].equals("w");
            int     moveNumber = (ply / 2) + 1;

            String uci = toUci(move);
            String san = toSan(board, move);

            boolean ok;
            try {
                ok = board.doMove(move);
            } catch (NullPointerException npe) {
                // FIX: loga fenBefore (FEN real no momento do erro) em vez de setupFen
                log.warn("Partida {} ply {}: NPE em doMove({}) — move inválido para FEN='{}'. "
                        + "rawMoveText disponível: {}", index, ply, uci, fenBefore, rawMoveText != null);
                break;
            }

            if (!ok) {
                log.warn("Lance ilegal {} na partida {}, ply {}", uci, index, ply);
                break;
            }

            entries.add(new MoveEntry(uci, san, fenBefore, board.getFen(), moveNumber, whiteTurn));
            ply++;
        }

        return new GameData(index, tags, setupFen, entries);
    }

    /**
     * Retorna uma MoveList com os Move objects resolvidos contra a FEN correta.
     * Para posição padrão retorna game.getHalfMoves() diretamente. Para FEN
     * customizada, usa o rawMoveText extraído do PGN bruto (estratégia mais
     * confiável) ou cai no toSanArray() como último recurso.
     *
     * FIX: Retorna null (em vez do halfMoves original incompatível) quando todas
     * as tentativas de re-parse falham para FEN customizada, evitando NPE no replay.
     */
    private MoveList resolveHalfMoves(int index, Game game, String setupFen, String rawMoveText) {
        MoveList halfMoves = game.getHalfMoves();

        if (START_FEN.equals(setupFen)) {
            return halfMoves; // posição padrão: sem problema
        }

        log.debug("Partida {}: FEN customizada detectada, re-parseando lances.", index);

        // Tentativa 1: rawMoveText extraído diretamente do PGN bruto (mais confiável)
        if (rawMoveText != null && !rawMoveText.isBlank()) {
            try {
                String cleaned = stripAnnotations(rawMoveText);
                if (!cleaned.isBlank()) {
                    MoveList reparsed = new MoveList(setupFen);
                    reparsed.loadFromSan(cleaned);
                    if (reparsed.size() > 0) {
                        log.debug("Partida {}: re-parse OK via rawMoveText ({} lances).", index, reparsed.size());
                        return reparsed;
                    }
                    log.warn("Partida {}: re-parse via rawMoveText retornou lista vazia.", index);
                }
            } catch (Exception e) {
                log.warn("Partida {}: falha no re-parse via rawMoveText: {}", index, e.getMessage());
            }
        } else {
            log.warn("Partida {}: rawMoveText é nulo/vazio após extração do PGN bruto.", index);
        }

        // Tentativa 2: SANs extraídas dos halfMoves já carregados
        // (as strings SAN são preservadas mesmo com Move objects corrompidos)
        try {
            String[] sanArray = halfMoves.toSanArray();
            if (sanArray != null && sanArray.length > 0) {
                String sanText = String.join(" ", sanArray);
                MoveList reparsed = new MoveList(setupFen);
                reparsed.loadFromSan(sanText);
                if (reparsed.size() > 0) {
                    log.debug("Partida {}: re-parse OK via toSanArray() ({} lances).", index, reparsed.size());
                    return reparsed;
                }
                log.warn("Partida {}: re-parse via toSanArray() retornou lista vazia.", index);
            }
        } catch (Exception e) {
            log.warn("Partida {}: falha no re-parse via toSanArray(): {}", index, e.getMessage());
        }

        // FIX: retorna null para sinalizar falha — não retorna halfMoves incompatível
        log.error(
                "Partida {}: todas tentativas de re-parse falharam para FEN='{}'. "
                        + "Retornando null para evitar NPE no replay.",
                index, setupFen);
        return null;
    }

    /**
     * Remove comentários ({ }), variantes ( ( ) ), anotações NAG ($N), numeração
     * de lances e token de resultado do texto PGN de lances. Necessário porque
     * MoveList.loadFromSan() rejeita esses elementos.
     *
     * FIX: normaliza roque com zeros ('0-0', '0-0-0') para forma com letra O
     * ('O-O', 'O-O-O'), aceita pelo MoveList.loadFromSan().
     */
    private String stripAnnotations(String moveText) {
        StringBuilder sb = new StringBuilder(moveText.length());
        int depth = 0;
        boolean inBrace = false;
        boolean inSemicolon = false;

        for (int i = 0; i < moveText.length(); i++) {
            char c = moveText.charAt(i);

            if (inSemicolon) {
                if (c == '\n') inSemicolon = false;
                continue;
            }
            if (inBrace) {
                if (c == '}') inBrace = false;
                continue;
            }
            if (c == '{') { inBrace = true; continue; }
            if (c == ';') { inSemicolon = true; continue; }
            if (c == '(') { depth++; continue; }
            if (c == ')') { if (depth > 0) depth--; continue; }
            if (depth == 0) sb.append(c);
        }

        return sb.toString()
                .replaceAll("\\$\\d+", " ")            // NAG: $1, $2...
                .replaceAll("1-0|0-1|1/2-1/2|\\*", " ") // token de resultado
                .replaceAll("\\d+\\.+", " ")            // numeração: 1. 1...
                // FIX: normaliza roque com zeros para forma com letra O
                // Deve ocorrer ANTES de qualquer outro replace que possa interferir
                .replaceAll("(?<![A-Za-z])0-0-0(?![A-Za-z0-9])", "O-O-O")
                .replaceAll("(?<![A-Za-z])0-0(?![A-Za-z0-9-])", "O-O")
                .replaceAll("\\s+", " ").trim();
    }

    // ── Exportação PGN ───────────────────────────────────────────

    /**
     * Serializa as partidas (com análise) para PGN anotado. Avaliações são
     * emitidas como comentários no formato Lichess: {@code { [%eval +0.28] }}
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
        String from  = move.getFrom().value().toLowerCase(Locale.ROOT);
        String to    = move.getTo().value().toLowerCase(Locale.ROOT);
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
     * Extrai o valor da HashMap retornada por PgnHolder.getEvent(), etc. O
     * PgnHolder retorna um Map onde a chave é o nome do evento e o valor é um
     * objeto Event/Site/Round com método getName().
     */
    private String extractPgnHolderValue(Object mapOrValue) {
        if (mapOrValue == null) return null;

        if (mapOrValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) mapOrValue;
            if (map.isEmpty()) return null;

            Object key = map.keySet().iterator().next();
            if (key != null) return key.toString();

            Object value = map.values().iterator().next();
            if (value != null) {
                try {
                    java.lang.reflect.Method getName = value.getClass().getMethod("getName");
                    return (String) getName.invoke(value);
                } catch (Exception e) {
                    return value.toString();
                }
            }
        }

        if (mapOrValue instanceof String) return (String) mapOrValue;
        return mapOrValue.toString();
    }

    private String getPropertyValue(Game game, String key) {
        if (game.getProperty() != null) {
            Object value = game.getProperty().get(key);
            if (value != null) return value.toString();
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
        while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        return sb.toString();
    }
}
