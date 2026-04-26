-- ============================================================
--  V1 - Schema inicial: partida + lance
--  Projeto : Chess Analyzer
--  Banco   : PostgreSQL 14+
--  Flyway  : V1__create_partida_lance_schema.sql
-- ============================================================

-- ------------------------------------------------------------
-- 1. SEQUENCES
--    allocationSize=1  para partida  (volume baixo)
--    allocationSize=50 para lance    (inserção em batch)
-- ------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS partida_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE IF NOT EXISTS lance_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 50;

-- ------------------------------------------------------------
-- 2. TABELA partida
--    Cada linha representa uma partida de xadrez completa.
--    Os campos do cabeçalho PGN (Seven-Tag Roster + extras)
--    são mapeados em colunas dedicadas.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS partida (

    -- Chave primária gerada pela sequence
    id                BIGINT      NOT NULL DEFAULT nextval('partida_id_seq'),

    -- ── Seven-Tag Roster (padrão PGN obrigatório) ─────────────
    event             VARCHAR(255),           -- [Event "..."]
    site              VARCHAR(255),           -- [Site "..."]
    date              VARCHAR(20),            -- [Date "YYYY.MM.DD"]
    round             VARCHAR(20),            -- [Round "..."]
    white             VARCHAR(255),           -- [White "..."]
    black             VARCHAR(255),           -- [Black "..."]
    result            VARCHAR(10),            -- [Result "1-0"|"0-1"|"1/2-1/2"|"*"]

    -- ── Campos extras Lichess / FIDE / chess.com ──────────────
    white_elo         VARCHAR(10),            -- [WhiteElo "..."]
    black_elo         VARCHAR(10),            -- [BlackElo "..."]
    white_rating_diff VARCHAR(10),            -- [WhiteRatingDiff "+N"|"-N"]
    black_rating_diff VARCHAR(10),            -- [BlackRatingDiff "+N"|"-N"]
    opening           VARCHAR(255),           -- [Opening "..."]
    eco               VARCHAR(10),            -- [ECO "B20"]
    time_control      VARCHAR(30),            -- [TimeControl "600+0"]
    termination       VARCHAR(100),           -- [Termination "Normal"]
    variant           VARCHAR(50),            -- [Variant "Standard"]
    utc_date          VARCHAR(20),            -- [UTCDate "..."]
    utc_time          VARCHAR(20),            -- [UTCTime "..."]

    -- ── Metadados internos ────────────────────────────────────
    initial_fen       VARCHAR(100),           -- FEN inicial (SetUp ou posição padrão)
    pgn_index         INT         NOT NULL,   -- índice 0-based no arquivo PGN de origem

    CONSTRAINT pk_partida PRIMARY KEY (id)
);

COMMENT ON TABLE  partida                    IS 'Partida de xadrez importada de arquivo PGN.';
COMMENT ON COLUMN partida.id                 IS 'Chave primária gerada pela sequence partida_id_seq.';
COMMENT ON COLUMN partida.event             IS 'Nome do torneio ou tipo da partida (tag PGN Event).';
COMMENT ON COLUMN partida.site              IS 'Cidade ou plataforma onde a partida foi jogada (tag PGN Site).';
COMMENT ON COLUMN partida.date              IS 'Data da partida no formato YYYY.MM.DD (tag PGN Date).';
COMMENT ON COLUMN partida.round             IS 'Rodada do torneio (tag PGN Round).';
COMMENT ON COLUMN partida.white             IS 'Nome ou username do jogador das peças brancas (tag PGN White).';
COMMENT ON COLUMN partida.black             IS 'Nome ou username do jogador das peças pretas (tag PGN Black).';
COMMENT ON COLUMN partida.result            IS 'Resultado da partida: 1-0, 0-1, 1/2-1/2 ou * (tag PGN Result).';
COMMENT ON COLUMN partida.white_elo         IS 'Rating ELO das brancas (tag PGN WhiteElo).';
COMMENT ON COLUMN partida.black_elo         IS 'Rating ELO das pretas (tag PGN BlackElo).';
COMMENT ON COLUMN partida.white_rating_diff IS 'Variação de rating das brancas após a partida.';
COMMENT ON COLUMN partida.black_rating_diff IS 'Variação de rating das pretas após a partida.';
COMMENT ON COLUMN partida.opening           IS 'Nome da abertura em formato longo (tag PGN Opening).';
COMMENT ON COLUMN partida.eco               IS 'Código ECO da abertura, ex: B20 (tag PGN ECO).';
COMMENT ON COLUMN partida.time_control      IS 'Controle de tempo no formato segundos+incremento, ex: 600+0.';
COMMENT ON COLUMN partida.termination       IS 'Motivo do término da partida: Normal, Time forfeit, Abandoned, etc.';
COMMENT ON COLUMN partida.variant           IS 'Variante do xadrez: Standard, Chess960, Crazyhouse, etc.';
COMMENT ON COLUMN partida.utc_date          IS 'Data UTC da partida em plataformas online como Lichess.';
COMMENT ON COLUMN partida.utc_time          IS 'Hora UTC da partida em plataformas online como Lichess.';
COMMENT ON COLUMN partida.initial_fen       IS 'FEN da posição inicial. Presente quando tag SetUp=1 ou sempre registrado.';
COMMENT ON COLUMN partida.pgn_index         IS 'Índice 0-based da partida no arquivo PGN de origem (para deduplicação).';

-- Índices para buscas frequentes
CREATE INDEX IF NOT EXISTS idx_partida_white  ON partida (lower(white));
CREATE INDEX IF NOT EXISTS idx_partida_black  ON partida (lower(black));
CREATE INDEX IF NOT EXISTS idx_partida_result ON partida (result);
CREATE INDEX IF NOT EXISTS idx_partida_eco    ON partida (eco);

-- ------------------------------------------------------------
-- 3. TABELA lance
--    Cada linha representa um meio-lance (ply).
--    O par (partida_id, ordem) é único dentro da partida.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS lance (

    -- Chave primária gerada pela sequence (allocationSize=50 para batch)
    id                  BIGINT      NOT NULL DEFAULT nextval('lance_id_seq'),

    -- ── Chave estrangeira ─────────────────────────────────────
    partida_id          BIGINT      NOT NULL,

    -- ── Posicionamento na partida ─────────────────────────────
    ordem               INT         NOT NULL,  -- posição sequencial 1-based dentro da partida
    numero_lance        INT         NOT NULL,  -- número do lance (brancas e pretas compartilham)
    vez_brancas         BOOLEAN     NOT NULL,  -- true = lance das peças brancas

    -- ── Notação do lance ──────────────────────────────────────
    uci                 VARCHAR(10) NOT NULL,  -- ex: "e2e4", "g1f3", "e7e8q"
    san                 VARCHAR(20) NOT NULL,  -- ex: "e4", "Nf3", "e8=Q"
    fen_antes           VARCHAR(100) NOT NULL, -- FEN da posição imediatamente ANTES do lance
    fen_depois          VARCHAR(100) NOT NULL, -- FEN da posição imediatamente APÓS o lance

    -- ── Análise Stockfish (nullable – preenchido assincronamente) ──
    eval                NUMERIC(7,2),          -- avaliação em peões (ex: +0.28, -1.35)
    mate_em             INT,                   -- mate forçado em N meios-lances (null = sem mate)
    melhor_lance        VARCHAR(10),           -- melhor resposta sugerida em notação UCI
    variante_principal  VARCHAR(500),          -- PV serializado separado por espaço
    analisado           BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_lance          PRIMARY KEY (id),
    CONSTRAINT fk_lance_partida  FOREIGN KEY (partida_id)
                                     REFERENCES partida (id)
                                     ON DELETE CASCADE
                                     ON UPDATE CASCADE,
    CONSTRAINT uq_lance_ordem    UNIQUE (partida_id, ordem)
);

COMMENT ON TABLE  lance                      IS 'Meio-lance (ply) de uma partida de xadrez. 1 lance brancas + 1 lance pretas = 2 plies.';
COMMENT ON COLUMN lance.id                   IS 'Chave primária gerada pela sequence lance_id_seq (allocationSize=50).';
COMMENT ON COLUMN lance.partida_id           IS 'FK para partida.id. CASCADE DELETE/UPDATE.';
COMMENT ON COLUMN lance.ordem                IS 'Posição sequencial 1-based do ply dentro da partida. Par único com partida_id.';
COMMENT ON COLUMN lance.numero_lance         IS 'Número do lance no sentido xadrez (1.e4 e 1...e5 têm numero_lance=1).';
COMMENT ON COLUMN lance.vez_brancas          IS 'TRUE se for o ply das peças brancas, FALSE para pretas.';
COMMENT ON COLUMN lance.uci                  IS 'Lance em notação UCI (Universal Chess Interface), ex: e2e4.';
COMMENT ON COLUMN lance.san                  IS 'Lance em notação SAN (Standard Algebraic Notation), ex: e4.';
COMMENT ON COLUMN lance.fen_antes            IS 'Posição FEN imediatamente antes de executar este lance.';
COMMENT ON COLUMN lance.fen_depois           IS 'Posição FEN imediatamente após executar este lance.';
COMMENT ON COLUMN lance.eval                 IS 'Avaliação Stockfish em peões, perspectiva das brancas. NULL se não analisado.';
COMMENT ON COLUMN lance.mate_em              IS 'Mate forçado em N meios-lances. NULL se não há mate forçado na posição.';
COMMENT ON COLUMN lance.melhor_lance         IS 'Melhor resposta sugerida pelo Stockfish em notação UCI.';
COMMENT ON COLUMN lance.variante_principal   IS 'Principal Variation (PV) do Stockfish, lances UCI separados por espaço.';
COMMENT ON COLUMN lance.analisado            IS 'TRUE quando o Stockfish concluiu a análise deste ply.';

-- Índices para buscas frequentes
CREATE INDEX IF NOT EXISTS idx_lance_partida_id     ON lance (partida_id);
CREATE INDEX IF NOT EXISTS idx_lance_partida_ordem  ON lance (partida_id, ordem);
CREATE INDEX IF NOT EXISTS idx_lance_nao_analisado  ON lance (partida_id, ordem)
    WHERE analisado = FALSE;   -- índice parcial: apenas lances pendentes
