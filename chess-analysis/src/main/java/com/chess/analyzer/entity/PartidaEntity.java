package com.chess.analyzer.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Entidade JPA que persiste uma partida de xadrez no PostgreSQL.
 *
 * <p>A identidade de negócio é representada pelo campo {@code gameId}:
 * quando o PGN contém a tag {@code [GameId "myF6FTAy"]}, esse valor é
 * usado diretamente. Caso contrário, é calculado um SHA-256 hex (64 chars)
 * sobre os campos do Seven-Tag Roster + FEN inicial.</p>
 */
@Entity
@Table(
    name = "partida",
    indexes = {
        @Index(name = "idx_partida_eco",     columnList = "eco"),
        @Index(name = "idx_partida_white",   columnList = "white"),
        @Index(name = "idx_partida_black",   columnList = "black"),
        @Index(name = "idx_partida_fonte",   columnList = "fonte_pgn"),
        @Index(name = "idx_partida_game_id", columnList = "game_id")
    }
)
public class PartidaEntity {

    // ── Chave primária ────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "partida_seq")
    @SequenceGenerator(
            name         = "partida_seq",
            sequenceName = "partida_id_seq",
            allocationSize = 1
    )
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Identificador estável de negócio.
     *
     * <ul>
     *   <li>PGN com tag {@code GameId} (ex: Lichess) → valor nativo, ex: {@code "myF6FTAy"}</li>
     *   <li>PGN sem a tag → SHA-256 hex (64 chars) dos campos identitários</li>
     * </ul>
     *
     * <p>Imutável após criação ({@code updatable = false}) e único no banco
     * ({@code unique = true}).</p>
     */
    @Column(name = "game_id", length = 255, nullable = false,
            unique = true, updatable = false)
    private String gameId;

    // ── Seven-Tag Roster ──────────────────────────────────────────────────
    @Column(name = "event",  length = 255) private String event;
    @Column(name = "site",   length = 255) private String site;

    @Column(name = "date") private LocalDate date;

    @Column(name = "round",  length = 20)  private String round;
    @Column(name = "white",  length = 255) private String white;
    @Column(name = "black",  length = 255) private String black;
    @Column(name = "result", length = 10)  private String result;

    // ── Campos extras (Lichess / FIDE / chess.com) ────────────────────────
    @Column(name = "white_elo") private Integer whiteElo;
    @Column(name = "black_elo") private Integer blackElo;
    @Column(name = "white_rating_diff") private Integer whiteRatingDiff;
    @Column(name = "black_rating_diff") private Integer blackRatingDiff;

    @Column(name = "opening",      length = 255) private String opening;
    @Column(name = "eco",          length = 10)  private String eco;
    @Column(name = "time_control", length = 30)  private String timeControl;
    @Column(name = "termination",  length = 100) private String termination;
    @Column(name = "variant",      length = 50)  private String variant;

    @Column(name = "utc_datetime") private LocalDateTime utcDatetime;

    // ── Metadados internos ────────────────────────────────────────────────
    @Column(name = "initial_fen", length = 100) private String initialFen;

    @Column(name = "pgn_index", nullable = false)
    private int pgnIndex;

    @Column(name = "fonte_pgn", length = 255)
    private String fontePgn;

    // ── Relação com lances ────────────────────────────────────────────────
    @OneToMany(mappedBy = "partida", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordem ASC")
    private List<LanceEntity> lances = new ArrayList<>();

    protected PartidaEntity() {}

    public PartidaEntity(int pgnIndex, String fontePgn, String gameId,
                         Map<String, String> tags, String initialFen) {
        this.pgnIndex   = pgnIndex;
        this.fontePgn   = fontePgn;
        this.gameId     = gameId;
        this.initialFen = initialFen;

        this.event  = tags.get("Event");
        this.site   = tags.get("Site");
        this.date   = parseDate(tags.get("Date"));
        this.round  = tags.get("Round");
        this.white  = tags.get("White");
        this.black  = tags.get("Black");
        this.result = tags.get("Result");

        this.whiteElo         = parseIntOrNull(tags.get("WhiteElo"));
        this.blackElo         = parseIntOrNull(tags.get("BlackElo"));
        this.whiteRatingDiff  = parseIntOrNull(tags.get("WhiteRatingDiff"));
        this.blackRatingDiff  = parseIntOrNull(tags.get("BlackRatingDiff"));
        this.opening          = tags.get("Opening");
        this.eco              = tags.get("ECO");
        this.timeControl      = tags.get("TimeControl");
        this.termination      = tags.get("Termination");
        this.variant          = tags.get("Variant");
        this.utcDatetime      = parseUtcDatetime(tags.get("UTCDate"), tags.get("UTCTime"));
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public Long          getId()              { return id; }
    public String        getGameId()          { return gameId; }
    public int           getPgnIndex()        { return pgnIndex; }
    public String        getFontePgn()        { return fontePgn; }
    public String        getEvent()           { return event; }
    public String        getSite()            { return site; }
    public LocalDate     getDate()            { return date; }
    public String        getRound()           { return round; }
    public String        getWhite()           { return white; }
    public String        getBlack()           { return black; }
    public String        getResult()          { return result; }
    public Integer       getWhiteElo()        { return whiteElo; }
    public Integer       getBlackElo()        { return blackElo; }
    public Integer       getWhiteRatingDiff() { return whiteRatingDiff; }
    public Integer       getBlackRatingDiff() { return blackRatingDiff; }
    public String        getOpening()         { return opening; }
    public String        getEco()             { return eco; }
    public String        getTimeControl()     { return timeControl; }
    public String        getTermination()     { return termination; }
    public String        getVariant()         { return variant; }
    public LocalDateTime getUtcDatetime()     { return utcDatetime; }
    public String        getInitialFen()      { return initialFen; }

    public List<LanceEntity> getLances() {
        return Collections.unmodifiableList(lances);
    }

    // ── Métodos de domínio ────────────────────────────────────────────────
    public void addLance(LanceEntity lance) {
        lance.associarPartida(this);
        lances.add(lance);
    }

    public void corrigirResultado(String novoResultado) {
        this.result = novoResultado;
    }

    public String titulo() {
        String w = nvl(white);
        String b = nvl(black);
        String e = event != null && !event.isBlank() ? event : "";
        String d = date  != null ? date.toString() : "";
        return ("%s vs %s — %s %s".formatted(w, b, e, d)).strip();
    }

    // ── Helpers de parse ──────────────────────────────────────────────────
    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.contains("?")) return null;
        try { return LocalDate.parse(raw.replace('.', '-')); }
        catch (Exception e) { return null; }
    }

    private static LocalDateTime parseUtcDatetime(String rawDate, String rawTime) {
        if (rawDate == null || rawTime == null
                || rawDate.contains("?") || rawTime.contains("?")) return null;
        try {
            LocalDate d = LocalDate.parse(rawDate.replace('.', '-'));
            String[] parts = rawTime.split(":");
            return d.atTime(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        } catch (Exception e) { return null; }
    }

    private static Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank() || "?".equals(raw.trim())) return null;
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String nvl(String v) {
        return (v == null || v.isBlank() || "?".equals(v)) ? "?" : v;
    }
}
