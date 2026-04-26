package com.chess.analyzer.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Entidade JPA que persiste uma partida de xadrez no PostgreSQL.
 *
 * <p>Estratégia de acesso: as anotações estão nos <em>campos</em>, portanto
 * o Hibernate usa {@code AccessType.FIELD} e não necessita de getters/setters
 * para ler ou gravar o estado. Os setters foram removidos intencionalmente;
 * o estado é definido no construtor de domínio e, quando necessário, por
 * métodos de negócio explícitos.</p>
 *
 * <p>O construtor sem argumentos é {@code protected} — visível apenas para
 * o Hibernate (que instancia a entidade via reflection) e para subclasses
 * de proxy, mas inacessível para o código de aplicação.</p>
 */
@Entity
@Table(name = "partida")
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

    // ── Seven-Tag Roster ──────────────────────────────────────────────────
    @Column(name = "event",  length = 255) private String event;
    @Column(name = "site",   length = 255) private String site;
    @Column(name = "date",   length = 20)  private String date;
    @Column(name = "round",  length = 20)  private String round;
    @Column(name = "white",  length = 255) private String white;
    @Column(name = "black",  length = 255) private String black;
    @Column(name = "result", length = 10)  private String result;

    // ── Campos extras (Lichess / FIDE / chess.com) ────────────────────────
    @Column(name = "white_elo",          length = 10)  private String whiteElo;
    @Column(name = "black_elo",          length = 10)  private String blackElo;
    @Column(name = "white_rating_diff",  length = 10)  private String whiteRatingDiff;
    @Column(name = "black_rating_diff",  length = 10)  private String blackRatingDiff;
    @Column(name = "opening",            length = 255) private String opening;
    @Column(name = "eco",                length = 10)  private String eco;
    @Column(name = "time_control",       length = 30)  private String timeControl;
    @Column(name = "termination",        length = 100) private String termination;
    @Column(name = "variant",            length = 50)  private String variant;
    @Column(name = "utc_date",           length = 20)  private String utcDate;
    @Column(name = "utc_time",           length = 20)  private String utcTime;

    // ── Metadados internos ────────────────────────────────────────────────
    @Column(name = "initial_fen", length = 100) private String initialFen;

    /** Índice 0-based da partida no arquivo PGN de origem. */
    @Column(name = "pgn_index", nullable = false)
    private int pgnIndex;

    // ── Relação com lances ────────────────────────────────────────────────
    @OneToMany(mappedBy = "partida", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordem ASC")
    private List<LanceEntity> lances = new ArrayList<>();

    // ── Construtor protegido — uso exclusivo do JPA/Hibernate ─────────────
    /** Exigido pela especificação JPA. Não utilizar no código de aplicação. */
    protected PartidaEntity() {}

    // ── Construtor de domínio ─────────────────────────────────────────────
    /**
     * Cria uma partida a partir dos metadados extraídos de um arquivo PGN.
     *
     * @param pgnIndex índice 0-based no arquivo PGN de origem
     * @param tags     mapa completo de tags do cabeçalho PGN
     * @param initialFen FEN da posição inicial (posição padrão ou SetUp)
     */
    public PartidaEntity(int pgnIndex, Map<String, String> tags, String initialFen) {
        this.pgnIndex   = pgnIndex;
        this.initialFen = initialFen;

        // Seven-Tag Roster
        this.event  = tags.get("Event");
        this.site   = tags.get("Site");
        this.date   = tags.get("Date");
        this.round  = tags.get("Round");
        this.white  = tags.get("White");
        this.black  = tags.get("Black");
        this.result = tags.get("Result");

        // Campos extras
        this.whiteElo         = tags.get("WhiteElo");
        this.blackElo         = tags.get("BlackElo");
        this.whiteRatingDiff  = tags.get("WhiteRatingDiff");
        this.blackRatingDiff  = tags.get("BlackRatingDiff");
        this.opening          = tags.get("Opening");
        this.eco              = tags.get("ECO");
        this.timeControl      = tags.get("TimeControl");
        this.termination      = tags.get("Termination");
        this.variant          = tags.get("Variant");
        this.utcDate          = tags.get("UTCDate");
        this.utcTime          = tags.get("UTCTime");
    }

    // ── Getters (somente leitura — sem setters) ───────────────────────────

    public Long   getId()              { return id; }
    public int    getPgnIndex()        { return pgnIndex; }
    public String getEvent()           { return event; }
    public String getSite()            { return site; }
    public String getDate()            { return date; }
    public String getRound()           { return round; }
    public String getWhite()           { return white; }
    public String getBlack()           { return black; }
    public String getResult()          { return result; }
    public String getWhiteElo()        { return whiteElo; }
    public String getBlackElo()        { return blackElo; }
    public String getWhiteRatingDiff() { return whiteRatingDiff; }
    public String getBlackRatingDiff() { return blackRatingDiff; }
    public String getOpening()         { return opening; }
    public String getEco()             { return eco; }
    public String getTimeControl()     { return timeControl; }
    public String getTermination()     { return termination; }
    public String getVariant()         { return variant; }
    public String getUtcDate()         { return utcDate; }
    public String getUtcTime()         { return utcTime; }
    public String getInitialFen()      { return initialFen; }

    /** Retorna uma visão não modificável da lista de lances. */
    public List<LanceEntity> getLances() {
        return Collections.unmodifiableList(lances);
    }

    // ── Métodos de domínio ────────────────────────────────────────────────

    /**
     * Adiciona um lance à partida, mantendo a consistência bidirecional.
     * É o único ponto de entrada para associar um {@link LanceEntity} a esta partida.
     */
    public void addLance(LanceEntity lance) {
        lance.associarPartida(this);
        lances.add(lance);
    }

    /** Atualiza o resultado da partida (ex: após correção de importação). */
    public void corrigirResultado(String novoResultado) {
        this.result = novoResultado;
    }

    /** Título legível para exibição: "White vs Black — Event Date". */
    public String titulo() {
        String w = nvl(white);
        String b = nvl(black);
        String e = event  != null && !event.isBlank()  ? event  : "";
        String d = date   != null && !date.isBlank()   ? date   : "";
        return ("%s vs %s — %s %s".formatted(w, b, e, d)).strip();
    }

    private static String nvl(String v) {
        return (v == null || v.isBlank() || "?".equals(v)) ? "?" : v;
    }
}
