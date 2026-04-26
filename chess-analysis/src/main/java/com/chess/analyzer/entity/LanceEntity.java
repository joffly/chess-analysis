package com.chess.analyzer.entity;

import jakarta.persistence.*;

/**
 * Entidade JPA que persiste um meio-lance (ply) de uma partida de xadrez.
 *
 * Cada linha representa um ply: e.g. 1.e4 (brancas) e 1...e5 (pretas)
 * são dois registros distintos com {@code ordem} 1 e 2 respectivamente.
 *
 * A FK {@code partida_id} aponta para {@link PartidaEntity}.
 * ID e sequence são gerenciados automaticamente pelo PostgreSQL.
 */
@Entity
@Table(
    name = "lance",
    indexes = {
        @Index(name = "idx_lance_partida_id", columnList = "partida_id"),
        @Index(name = "idx_lance_partida_ordem", columnList = "partida_id, ordem")
    }
)
public class LanceEntity {

    // ── Chave primária ────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lance_seq")
    @SequenceGenerator(
            name         = "lance_seq",
            sequenceName = "lance_id_seq",
            allocationSize = 50   // batch insert eficiente
    )
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // ── Chave estrangeira ─────────────────────────────────────────────────

    /** Partida à qual este lance pertence. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partida_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_lance_partida"))
    private PartidaEntity partida;

    // ── Dados do lance ────────────────────────────────────────────────────

    /**
     * Posição sequencial do lance dentro da partida (1-based).
     * O par (partida_id, ordem) identifica unicamente um ply.
     */
    @Column(name = "ordem", nullable = false)
    private int ordem;

    /** Número do lance (brancas e pretas compartilham o mesmo número). */
    @Column(name = "numero_lance", nullable = false)
    private int numeroLance;

    /** {@code true} se for o lance das peças brancas. */
    @Column(name = "vez_brancas", nullable = false)
    private boolean vezBrancas;

    /** Notação UCI do lance (ex: "e2e4", "g1f3", "e7e8q"). */
    @Column(name = "uci", length = 10, nullable = false)
    private String uci;

    /** Notação SAN do lance (ex: "e4", "Nf3", "e8=Q"). */
    @Column(name = "san", length = 20, nullable = false)
    private String san;

    /** FEN da posição imediatamente ANTES deste lance. */
    @Column(name = "fen_antes", length = 100, nullable = false)
    private String fenAntes;

    /** FEN da posição imediatamente APÓS este lance. */
    @Column(name = "fen_depois", length = 100, nullable = false)
    private String fenDepois;

    // ── Resultados de análise Stockfish (opcionais) ───────────────────────

    /** Avaliação em centipeões / 100, perspectiva das brancas. {@code null} se não analisado. */
    @Column(name = "eval")
    private Double eval;

    /** Mate forçado em N meios-lances. {@code null} se não há mate forçado. */
    @Column(name = "mate_em")
    private Integer mateEm;

    /** Melhor lance sugerido pelo Stockfish em notação UCI. */
    @Column(name = "melhor_lance", length = 10)
    private String melhorLance;

    /** Variante principal (PV) serializada como string separada por espaços. */
    @Column(name = "variante_principal", length = 500)
    private String variantePrincipal;

    /** {@code true} se este lance já foi analisado pelo Stockfish. */
    @Column(name = "analisado", nullable = false)
    private boolean analisado = false;

    // ── Construtor padrão JPA ─────────────────────────────────────────────
    public LanceEntity() {}

    // ── Getters e Setters ─────────────────────────────────────────────────

    public Long getId()                              { return id; }

    public PartidaEntity getPartida()                { return partida; }
    public void          setPartida(PartidaEntity p) { this.partida = p; }

    public int  getOrdem()                           { return ordem; }
    public void setOrdem(int ordem)                  { this.ordem = ordem; }

    public int  getNumeroLance()                     { return numeroLance; }
    public void setNumeroLance(int n)                { this.numeroLance = n; }

    public boolean isVezBrancas()                    { return vezBrancas; }
    public void    setVezBrancas(boolean b)          { this.vezBrancas = b; }

    public String getUci()                           { return uci; }
    public void   setUci(String uci)                 { this.uci = uci; }

    public String getSan()                           { return san; }
    public void   setSan(String san)                 { this.san = san; }

    public String getFenAntes()                      { return fenAntes; }
    public void   setFenAntes(String fen)            { this.fenAntes = fen; }

    public String getFenDepois()                     { return fenDepois; }
    public void   setFenDepois(String fen)           { this.fenDepois = fen; }

    public Double  getEval()                         { return eval; }
    public void    setEval(Double eval)              { this.eval = eval; }

    public Integer getMateEm()                       { return mateEm; }
    public void    setMateEm(Integer m)              { this.mateEm = m; }

    public String getMelhorLance()                   { return melhorLance; }
    public void   setMelhorLance(String m)           { this.melhorLance = m; }

    public String getVariantePrincipal()             { return variantePrincipal; }
    public void   setVariantePrincipal(String vp)    { this.variantePrincipal = vp; }

    public boolean isAnalisado()                     { return analisado; }
    public void    setAnalisado(boolean a)           { this.analisado = a; }
}
