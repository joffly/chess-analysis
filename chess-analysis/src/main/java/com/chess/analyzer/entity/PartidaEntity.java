package com.chess.analyzer.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade JPA que persiste uma partida de xadrez no PostgreSQL.
 *
 * Cada campo do cabeçalho PGN (Seven-Tag Roster + extras Lichess/FIDE)
 * é mapeado em sua própria coluna. A relação com os lances é feita
 * via {@link LanceEntity} com FK {@code partida_id}.
 *
 * ID e sequence são gerenciados automaticamente pelo PostgreSQL
 * (tipo BIGSERIAL via estratégia SEQUENCE do Hibernate).
 */
@Entity
@Table(name = "partida")
public class PartidaEntity {

    // ── Chave primária ────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "partida_seq")
    @SequenceGenerator(
            name      = "partida_seq",
            sequenceName = "partida_id_seq",
            allocationSize = 1
    )
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // ── Seven-Tag Roster (obrigatórios no padrão PGN) ─────────────────────

    /** [Event "..."] — Nome do torneio ou partida amigável. */
    @Column(name = "event", length = 255)
    private String event;

    /** [Site "..."] — Cidade/plataforma onde a partida foi jogada. */
    @Column(name = "site", length = 255)
    private String site;

    /** [Date "YYYY.MM.DD"] — Data da partida no formato PGN. */
    @Column(name = "date", length = 20)
    private String date;

    /** [Round "..."] — Rodada do torneio. */
    @Column(name = "round", length = 20)
    private String round;

    /** [White "..."] — Nome/username do jogador das peças brancas. */
    @Column(name = "white", length = 255)
    private String white;

    /** [Black "..."] — Nome/username do jogador das peças pretas. */
    @Column(name = "black", length = 255)
    private String black;

    /** [Result "1-0" | "0-1" | "1/2-1/2" | "*"] — Resultado da partida. */
    @Column(name = "result", length = 10)
    private String result;

    // ── Campos extras comuns (Lichess / FIDE / chess.com) ─────────────────

    /** [WhiteElo "..."] — ELO/rating das brancas. */
    @Column(name = "white_elo", length = 10)
    private String whiteElo;

    /** [BlackElo "..."] — ELO/rating das pretas. */
    @Column(name = "black_elo", length = 10)
    private String blackElo;

    /** [WhiteRatingDiff "+N" | "-N"] — Variação de rating das brancas. */
    @Column(name = "white_rating_diff", length = 10)
    private String whiteRatingDiff;

    /** [BlackRatingDiff "+N" | "-N"] — Variação de rating das pretas. */
    @Column(name = "black_rating_diff", length = 10)
    private String blackRatingDiff;

    /** [Opening "..."] — Nome da abertura (ECO long name). */
    @Column(name = "opening", length = 255)
    private String opening;

    /** [ECO "..."] — Código ECO da abertura (ex: "B20"). */
    @Column(name = "eco", length = 10)
    private String eco;

    /** [TimeControl "..."] — Controle de tempo (ex: "600+0", "180+2"). */
    @Column(name = "time_control", length = 30)
    private String timeControl;

    /** [Termination "..."] — Motivo do término (Normal, Time forfeit, etc.). */
    @Column(name = "termination", length = 100)
    private String termination;

    /** [Variant "..."] — Variante do xadrez (Standard, Chess960, etc.). */
    @Column(name = "variant", length = 50)
    private String variant;

    /** [UTCDate "..."] — Data UTC da partida online (Lichess). */
    @Column(name = "utc_date", length = 20)
    private String utcDate;

    /** [UTCTime "..."] — Hora UTC da partida online (Lichess). */
    @Column(name = "utc_time", length = 20)
    private String utcTime;

    /**
     * FEN inicial da partida. Presente quando o tag [SetUp "1"] existe;
     * caso contrário armazena a posição inicial padrão do xadrez.
     */
    @Column(name = "initial_fen", length = 100)
    private String initialFen;

    /** Índice (0-based) da partida no arquivo PGN de origem. */
    @Column(name = "pgn_index", nullable = false)
    private int pgnIndex;

    // ── Relação com lances ────────────────────────────────────────────────

    /**
     * Lista de lances da partida, ordenados por {@link LanceEntity#getOrdem()}.
     * Cascade ALL garante que salvar/remover uma partida propaga para os lances.
     * orphanRemoval remove lances sem partida associada.
     */
    @OneToMany(mappedBy = "partida", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordem ASC")
    private List<LanceEntity> lances = new ArrayList<>();

    // ── Construtores ──────────────────────────────────────────────────────

    /** Construtor padrão exigido pelo JPA. */
    public PartidaEntity() {}

    // ── Getters e Setters ─────────────────────────────────────────────────

    public Long getId()                          { return id; }

    public String getEvent()                     { return event; }
    public void   setEvent(String event)         { this.event = event; }

    public String getSite()                      { return site; }
    public void   setSite(String site)           { this.site = site; }

    public String getDate()                      { return date; }
    public void   setDate(String date)           { this.date = date; }

    public String getRound()                     { return round; }
    public void   setRound(String round)         { this.round = round; }

    public String getWhite()                     { return white; }
    public void   setWhite(String white)         { this.white = white; }

    public String getBlack()                     { return black; }
    public void   setBlack(String black)         { this.black = black; }

    public String getResult()                    { return result; }
    public void   setResult(String result)       { this.result = result; }

    public String getWhiteElo()                  { return whiteElo; }
    public void   setWhiteElo(String whiteElo)   { this.whiteElo = whiteElo; }

    public String getBlackElo()                  { return blackElo; }
    public void   setBlackElo(String blackElo)   { this.blackElo = blackElo; }

    public String getWhiteRatingDiff()           { return whiteRatingDiff; }
    public void   setWhiteRatingDiff(String v)   { this.whiteRatingDiff = v; }

    public String getBlackRatingDiff()           { return blackRatingDiff; }
    public void   setBlackRatingDiff(String v)   { this.blackRatingDiff = v; }

    public String getOpening()                   { return opening; }
    public void   setOpening(String opening)     { this.opening = opening; }

    public String getEco()                       { return eco; }
    public void   setEco(String eco)             { this.eco = eco; }

    public String getTimeControl()               { return timeControl; }
    public void   setTimeControl(String tc)      { this.timeControl = tc; }

    public String getTermination()               { return termination; }
    public void   setTermination(String t)       { this.termination = t; }

    public String getVariant()                   { return variant; }
    public void   setVariant(String variant)     { this.variant = variant; }

    public String getUtcDate()                   { return utcDate; }
    public void   setUtcDate(String utcDate)     { this.utcDate = utcDate; }

    public String getUtcTime()                   { return utcTime; }
    public void   setUtcTime(String utcTime)     { this.utcTime = utcTime; }

    public String getInitialFen()                { return initialFen; }
    public void   setInitialFen(String fen)      { this.initialFen = fen; }

    public int  getPgnIndex()                    { return pgnIndex; }
    public void setPgnIndex(int pgnIndex)        { this.pgnIndex = pgnIndex; }

    public List<LanceEntity> getLances()         { return lances; }
    public void setLances(List<LanceEntity> l)   { this.lances = l; }

    /** Utilitário: adiciona um lance mantendo o bidirecional. */
    public void addLance(LanceEntity lance) {
        lance.setPartida(this);
        this.lances.add(lance);
    }
}
