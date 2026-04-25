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
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Square;
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
     * Extrai o texto de lances de cada partida diretamente do conteúdo PGN bruto.
     */
    private Map<Integer, String> extractRawMoveTexts(String pgnContent) {
        Map<Integer, String> map = new LinkedHashMap<>();
        String[] blocks = pgnContent.split("\n\\s*\n(?=\\[)");
        int gameIndex = 0;
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;
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

        // ── Obtém lista de SANs ─────────────────────────────────────
        List<String> sans = resolveSans(index, game, setupFen, rawMoveText);
        if (sans == null || sans.isEmpty()) {
            log.error("Partida {}: impossível resolver lances para FEN='{}'. Partida ignorada.", index, setupFen);
            return new GameData(index, tags, setupFen, List.of());
        }

        // ── Replay com matching em legalMoves() ─────────────────────
        //
        // Para cada SAN, parseia um Move temporário e localiza o lance legal
        // equivalente no Board corrente. Isso resolve o problema do chesslib que
        // representa roque como Move(E8,H8) internamente mas MoveList.loadFromSan()
        // gera Move(E8,G8) — incompatibilidade que causava ok=false no doMove().
        List<MoveEntry> entries = new ArrayList<>(sans.size());
        Board board = new Board();
        board.loadFromFen(setupFen);

        // MoveList auxiliar usada apenas para converter SAN -> Move temporário
        MoveList temp = new MoveList(setupFen);

        for (int ply = 0; ply < sans.size(); ply++) {
            String sanToken = sans.get(ply);
            String fenBefore = board.getFen();
            boolean whiteTurn = fenBefore.split("\\s+")[1].equals("w");
            int moveNumber = (ply / 2) + 1;

            // Parseia SAN para obter from/to
            Move candidate;
            try {
                temp.clear();
                temp.loadFromSan(sanToken);
                if (temp.isEmpty()) {
                    log.warn("Partida {} ply {}: não foi possível parsear SAN '{}'. Interrompendo.",
                            index, ply, sanToken);
                    break;
                }
                candidate = temp.get(0);
            } catch (Exception e) {
                log.warn("Partida {} ply {}: erro ao parsear SAN '{}': {}. Interrompendo.",
                        index, ply, sanToken, e.getMessage());
                break;
            }

            // Localiza o Move legal correspondente no Board atual
            Move legal = resolveMove(board, candidate);
            if (legal == null) {
                log.warn("Partida {} ply {}: lance '{}' não encontrado nos lances legais (FEN='{}'). Interrompendo.",
                        index, ply, sanToken, fenBefore);
                break;
            }

            String uci = toUci(legal);
            String san = buildSan(board, legal, sanToken);

            boolean ok;
            try {
                ok = board.doMove(legal);
            } catch (Exception ex) {
                log.warn("Partida {} ply {}: excecao em doMove({}) FEN='{}': {}",
                        index, ply, uci, fenBefore, ex.getMessage());
                break;
            }

            if (!ok) {
                log.warn("Lance ilegal {} na partida {}, ply {} (FEN='{}')", uci, index, ply, fenBefore);
                break;
            }

            entries.add(new MoveEntry(uci, san, fenBefore, board.getFen(), moveNumber, whiteTurn));
        }

        return new GameData(index, tags, setupFen, entries);
    }

    /**
     * Encontra o {@link Move} legal no Board cujo par (from, to) corresponde ao
     * lance candidato. Para promoção, filtra também pela peça promovida.
     *
     * Isso é necessário porque o chesslib representa roque internamente com
     * destino na torre (ex.: E1→H1), enquanto MoveList.loadFromSan() gera
     * destino no rei (E1→G1). O matching por (from, to) via legalMoves()
     * garante o objeto correto para board.doMove().
     */
    private Move resolveMove(Board board, Move candidate) {
        Square fromSq = candidate.getFrom();
        Square toSq   = candidate.getTo();
        Piece  promo  = candidate.getPromotion();

        for (Move legal : board.legalMoves()) {
            if (!legal.getFrom().equals(fromSq)) continue;

            // Para roque: o candidato tem to=G1/G8 (casas do rei),
            // mas o lance legal pode ter to=H1/H8 (casas da torre, estilo Chess960).
            // Aceitamos ambos: verificação pelo tipo de peça + mesmo lado (arquivo).
            Piece piece = board.getPiece(fromSq);
            if (piece != null && piece != Piece.NONE
                    && piece.getPieceType() == PieceType.KING) {
                int candidateFileDiff = toSq.getFile().ordinal() - fromSq.getFile().ordinal();
                int legalFileDiff     = legal.getTo().getFile().ordinal() - fromSq.getFile().ordinal();
                // Roque rei: candidato vai para G (diff=+2) ou H (diff=+3)
                // Roque dama: candidato vai para C (diff=-2) ou A (diff=-4)
                boolean candidateKingSide = candidateFileDiff > 0;
                boolean legalKingSide     = legalFileDiff > 0;
                if (Math.abs(candidateFileDiff) >= 2 && Math.abs(legalFileDiff) >= 2) {
                    if (candidateKingSide == legalKingSide) return legal;
                    continue;
                }
            }

            if (!legal.getTo().equals(toSq)) continue;

            // Promoção: filtra pela peça desejada
            if (promo != null && promo != Piece.NONE) {
                if (!promo.equals(legal.getPromotion())) continue;
            }

            return legal;
        }
        return null;
    }

    /**
     * Retorna lista de tokens SAN limpos para replay.
     * Para posição padrão usa os SANs do game.getHalfMoves().
     * Para FEN customizada usa rawMoveText (mais confiável) ou toSanArray().
     */
    private List<String> resolveSans(int index, Game game, String setupFen, String rawMoveText) {
        // Posição padrão: extrai SANs do halfMoves já carregado
        if (START_FEN.equals(setupFen)) {
            MoveList hm = game.getHalfMoves();
            if (hm != null && !hm.isEmpty()) {
                try {
                    String[] arr = hm.toSanArray();
                    if (arr != null && arr.length > 0) return List.of(arr);
                } catch (Exception e) {
                    log.warn("Partida {}: falha ao extrair SANs do halfMoves padrão: {}", index, e.getMessage());
                }
            }
        }

        // FEN customizada — Tentativa 1: rawMoveText (texto PGN bruto, mais confiável)
        if (rawMoveText != null && !rawMoveText.isBlank()) {
            try {
                String cleaned = stripAnnotations(rawMoveText);
                if (!cleaned.isBlank()) {
                    List<String> tokens = List.of(cleaned.split("\\s+"));
                    if (!tokens.isEmpty()) {
                        log.debug("Partida {}: {} SANs extraídas via rawMoveText.", index, tokens.size());
                        return tokens;
                    }
                }
            } catch (Exception e) {
                log.warn("Partida {}: falha ao processar rawMoveText: {}", index, e.getMessage());
            }
        }

        // Tentativa 2: toSanArray() do halfMoves
        try {
            MoveList hm = game.getHalfMoves();
            if (hm != null) {
                String[] arr = hm.toSanArray();
                if (arr != null && arr.length > 0) {
                    List<String> tokens = new ArrayList<>();
                    for (String s : arr) {
                        if (s != null && !s.isBlank()) tokens.add(s.trim());
                    }
                    if (!tokens.isEmpty()) {
                        log.debug("Partida {}: {} SANs via toSanArray().", index, tokens.size());
                        return tokens;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Partida {}: falha em toSanArray(): {}", index, e.getMessage());
        }

        log.error("Partida {}: todas tentativas de resolver SANs falharam.", index);
        return null;
    }

    /**
     * Remove comentários, variantes, NAGs, numeração e resultado do texto PGN.
     * Normaliza roque com zeros ('0-0' -> 'O-O').
     */
    private String stripAnnotations(String moveText) {
        StringBuilder sb = new StringBuilder(moveText.length());
        int depth = 0;
        boolean inBrace = false;
        boolean inSemicolon = false;

        for (int i = 0; i < moveText.length(); i++) {
            char c = moveText.charAt(i);
            if (inSemicolon) { if (c == '\n') inSemicolon = false; continue; }
            if (inBrace)     { if (c == '}')  inBrace = false;     continue; }
            if (c == '{')    { inBrace = true;      continue; }
            if (c == ';')    { inSemicolon = true;  continue; }
            if (c == '(')    { depth++;              continue; }
            if (c == ')')    { if (depth > 0) depth--; continue; }
            if (depth == 0)  sb.append(c);
        }

        return sb.toString()
                .replaceAll("\\$\\d+", " ")             // NAG
                .replaceAll("1-0|0-1|1/2-1/2|\\*", " ") // resultado
                .replaceAll("\\d+\\.+", " ")             // numeração
                .replaceAll("(?<![A-Za-z])0-0-0(?![A-Za-z0-9])", "O-O-O") // roque dama
                .replaceAll("(?<![A-Za-z])0-0(?![A-Za-z0-9-])", "O-O")   // roque rei
                .replaceAll("\\s+", " ").trim();
    }

    /**
     * Gera SAN para o lance legal a partir do Board atual.
     * Para roque, retorna diretamente o token SAN original (já está correto).
     * Para demais lances, usa buildSan().
     */
    private String buildSan(Board board, Move move, String originalSan) {
        // Roque: usa o token SAN original (O-O / O-O-O) — já correto
        Piece piece = board.getPiece(move.getFrom());
        if (piece != null && piece != Piece.NONE && piece.getPieceType() == PieceType.KING) {
            int fileDiff = move.getTo().getFile().ordinal() - move.getFrom().getFile().ordinal();
            if (Math.abs(fileDiff) >= 2) {
                // Adiciona xeque/xeque-mate ao token original se necessário
                String base = fileDiff > 0 ? "O-O" : "O-O-O";
                return addCheckSuffix(board, move, base);
            }
        }
        return buildSanFull(board, move);
    }

    /**
     * Gera SAN completa para um lance legal: peça, desambiguação, captura,
     * destino, promoção, xeque/mate.
     */
    private String buildSanFull(Board board, Move move) {
        Square from  = move.getFrom();
        Square to    = move.getTo();
        Piece  piece = board.getPiece(from);

        if (piece == null || piece == Piece.NONE)
            return from.value().toLowerCase(Locale.ROOT) + to.value().toLowerCase(Locale.ROOT);

        PieceType type = piece.getPieceType();
        StringBuilder sb = new StringBuilder();

        if (type != PieceType.PAWN) sb.append(type.getSanSymbol());

        if (type != PieceType.PAWN) {
            boolean needFile = false, needRank = false;
            for (Move other : board.legalMoves()) {
                if (other.equals(move)) continue;
                if (!other.getTo().equals(to)) continue;
                Piece op = board.getPiece(other.getFrom());
                if (op == null || op.getPieceType() != type) continue;
                if (other.getFrom().getFile() == from.getFile()) needRank = true;
                else needFile = true;
            }
            String fromStr = from.value().toLowerCase(Locale.ROOT);
            if (needFile) sb.append(fromStr.charAt(0));
            if (needRank) sb.append(fromStr.charAt(1));
        } else {
            boolean isCapture = board.getPiece(to) != Piece.NONE || board.getEnPassant() == to;
            if (isCapture) sb.append(from.getFile().getNotation().toLowerCase(Locale.ROOT));
        }

        boolean isCapture = board.getPiece(to) != Piece.NONE || board.getEnPassant() == to;
        if (isCapture) sb.append('x');
        sb.append(to.value().toLowerCase(Locale.ROOT));

        if (move.getPromotion() != null && move.getPromotion() != Piece.NONE)
            sb.append('=').append(move.getPromotion().getPieceType().getSanSymbol().toUpperCase(Locale.ROOT));

        return addCheckSuffix(board, move, sb.toString());
    }

    /** Executa o lance numa cópia do board para detectar xeque/xeque-mate. */
    private String addCheckSuffix(Board board, Move move, String san) {
        try {
            Board copy = board.clone();
            copy.doMove(move);
            if (copy.isMated())         return san + "#";
            if (copy.isKingAttacked())  return san + "+";
        } catch (Exception e) {
            log.debug("addCheckSuffix: erro ao clonar board — {}", e.getMessage());
        }
        return san;
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

        List<MoveEntry> moves = game.getMoves();
        for (int i = 0; i < moves.size(); i++) {
            MoveEntry m = moves.get(i);
            if (m.isWhiteTurn()) sb.append(m.getMoveNumber()).append(". ");
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
