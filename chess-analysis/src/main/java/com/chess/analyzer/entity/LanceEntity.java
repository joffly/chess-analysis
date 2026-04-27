package com.chess.analyzer.entity;

import jakarta.persistence.*;

import java.util.List;

/**
 * Entidade JPA que persiste um meio-lance (ply) de uma partida de xadrez.
 *
 * <p>Estratégia de acesso: {@code AccessType.FIELD} — anotações nos campos,
 * sem necessidade de getters/setters para o ORM. Setters removidos;
 * estado imutável definido em construção, exceto pelos campos de análise
 * Stockfish que são preenchidos assincronamente via {@link #registrarAnalise}.</p>
 *
 * <p>Construtor sem argumentos {@code protected} — visível apenas para o
 * Hibernate (instanciação via proxy/reflection).</p>
 */
@Entity
@Table(
    name = "lance",
    indexes = {
        @Index(name = "idx_lance_partida_id",    columnList = "partida_id"),
        @Index(name = "idx_lance_partida_ordem", columnList = "partida_id, ordem"),
        @Index(name = "idx_lance_fen_depois",    columnList = "fen_depois")
    }
)
public class LanceEntity {

    // ── Chave primária ────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lance_seq")
    @SequenceGenerator(
            name           = "lance_seq",
            sequenceName   = "lance_id_seq",
            allocationSize = 50
    )
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // ── Chave estrangeira ─────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partida_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_lance_partida"))
    private PartidaEntity partida;

    // ── Dados do lance (imutáveis após construção) ────────────────────────
    @Column(name = "ordem",        nullable = false) private int     ordem;
    @Column(name = "numero_lance", nullable = false) private int     numeroLance;
    @Column(name = "vez_brancas",  nullable = false) private boolean vezBrancas;
    @Column(name = "uci",  length = 10,  nullable = false) private String uci;
    @Column(name = "san",  length = 20,  nullable = false) private String san;
    @Column(name = "fen_antes",  length = 100, nullable = false) private String fenAntes;
    @Column(name = "fen_depois", length = 100, nullable = false) private String fenDepois;

    // ── Análise Stockfish (nullable — preenchida assincronamente) ─────────
    @Column(name = "eval")                                           private Double  eval;
    @Column(name = "mate_em")                                        private Integer mateEm;
    @Column(name = "melhor_lance",       length = 10)                private String  melhorLance;
    @Column(name = "variante_principal", columnDefinition = "TEXT")  private String  variantePrincipal;
    @Column(name = "analisado", nullable = false)                    private boolean analisado = false;

    // ── Construtor protegido — uso exclusivo do JPA/Hibernate ─────────────
    /** Exigido pela especificação JPA. Não utilizar no código de aplicação. */
    protected LanceEntity() {}

    // ── Construtor de domínio ─────────────────────────────────────────────
    /**
     * Cria um lance com todos os dados obrigatórios.
     *
     * @param ordem       posição sequencial 1-based dentro da partida
     * @param numeroLance número do lance no sentido xadrez (1.e4 e 1...e5 = numero 1)
     * @param vezBrancas  {@code true} se for o ply das brancas
     * @param uci         notação UCI (ex: "e2e4")
     * @param san         notação SAN (ex: "e4")
     * @param fenAntes    FEN antes do lance
     * @param fenDepois   FEN depois do lance
     */
    public LanceEntity(int ordem, int numeroLance, boolean vezBrancas,
                       String uci, String san,
                       String fenAntes, String fenDepois) {
        this.ordem       = ordem;
        this.numeroLance = numeroLance;
        this.vezBrancas  = vezBrancas;
        this.uci         = uci;
        this.san         = san;
        this.fenAntes    = fenAntes;
        this.fenDepois   = fenDepois;
    }

    // ── Getters (somente leitura) ─────────────────────────────────────────

    public Long          getId()                { return id; }
    public PartidaEntity getPartida()           { return partida; }
    public int           getOrdem()             { return ordem; }
    public int           getNumeroLance()       { return numeroLance; }
    public boolean       isVezBrancas()         { return vezBrancas; }
    public String        getUci()               { return uci; }
    public String        getSan()               { return san; }
    public String        getFenAntes()          { return fenAntes; }
    public String        getFenDepois()         { return fenDepois; }
    public Double        getEval()              { return eval; }
    public Integer       getMateEm()            { return mateEm; }
    public String        getMelhorLance()       { return melhorLance; }
    public String        getVariantePrincipal() { return variantePrincipal; }
    public boolean       isAnalisado()          { return analisado; }

    // ── Métodos de domínio ────────────────────────────────────────────────

    /**
     * Registra o resultado da análise do Stockfish.
     * Único ponto de escrita para os campos de análise.
     *
     * @param eval              avaliação em peões (perspectiva brancas)
     * @param mateEm            mate forçado em N meios-lances; {@code null} se inexistente
     * @param melhorLance       melhor resposta em UCI
     * @param variantePrincipal PV serializado (lances UCI separados por espaço)
     */
    public void registrarAnalise(Double eval, Integer mateEm,
                                 String melhorLance, List<String> variantePrincipal) {
        this.eval              = eval;
        this.mateEm            = mateEm;
        this.melhorLance       = melhorLance;
        this.variantePrincipal = variantePrincipal != null
                                 ? String.join(" ", variantePrincipal)
                                 : null;
        this.analisado         = true;
    }

    /**
     * Vincula este lance à sua partida.
     * Chamado exclusivamente por {@link PartidaEntity#addLance(LanceEntity)}.
     */
    void associarPartida(PartidaEntity partida) {
        this.partida = partida;
    }

    /** Formatação legível da avaliação: "+0.28", "-1.35", "+M3", "-M5", "?". */
    public String evalFormatado() {
        if (!analisado) return "?";
        if (mateEm != null) return (mateEm > 0 ? "+M" : "-M") + Math.abs(mateEm);
        if (eval   == null) return "?";
        return "%+.2f".formatted(eval);
    }
}
