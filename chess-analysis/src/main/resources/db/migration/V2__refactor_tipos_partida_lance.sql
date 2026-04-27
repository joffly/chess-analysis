-- ============================================================
--  V2 - Refatoracao de tipos e novos campos
--  Projeto : Chess Analyzer
--  Banco   : PostgreSQL 14+
--  Flyway  : V2__refactor_tipos_partida_lance.sql
--
--  Alteracoes aplicadas:
--    partida:
--      1. white_elo / black_elo        : VARCHAR(10) -> INTEGER
--      2. white_rating_diff / black_rating_diff : VARCHAR(10) -> INTEGER
--      3. date                         : VARCHAR(20) -> DATE
--      4. utc_date + utc_time          : dois VARCHAR -> utc_datetime TIMESTAMPTZ
--      5. fonte_pgn                    : coluna nova VARCHAR(255)
--    lance:
--      6. variante_principal           : VARCHAR(500) -> TEXT
--      7. Novo indice em fen_depois para busca de posicoes.
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. white_elo: VARCHAR(10) -> INTEGER
--    Converte valores numericos; descarta '?', texto ou nulo.
-- ------------------------------------------------------------
ALTER TABLE partida
    ALTER COLUMN white_elo
        TYPE INTEGER
        USING CASE
                  WHEN white_elo ~ '^-?[0-9]+$' THEN white_elo::INTEGER
                  ELSE NULL
              END;

COMMENT ON COLUMN partida.white_elo
    IS 'Rating ELO das brancas (INTEGER). NULL quando ausente ou invalido no PGN.';

-- ------------------------------------------------------------
-- 2. black_elo: VARCHAR(10) -> INTEGER
-- ------------------------------------------------------------
ALTER TABLE partida
    ALTER COLUMN black_elo
        TYPE INTEGER
        USING CASE
                  WHEN black_elo ~ '^-?[0-9]+$' THEN black_elo::INTEGER
                  ELSE NULL
              END;

COMMENT ON COLUMN partida.black_elo
    IS 'Rating ELO das pretas (INTEGER). NULL quando ausente ou invalido no PGN.';

-- ------------------------------------------------------------
-- 3. white_rating_diff: VARCHAR(10) -> INTEGER
--    PGN Lichess inclui sinal (+12, -8); regex aceita ambos.
-- ------------------------------------------------------------
ALTER TABLE partida
    ALTER COLUMN white_rating_diff
        TYPE INTEGER
        USING CASE
                  WHEN white_rating_diff ~ '^[+-]?[0-9]+$' THEN white_rating_diff::INTEGER
                  ELSE NULL
              END;

COMMENT ON COLUMN partida.white_rating_diff
    IS 'Variacao de rating das brancas apos a partida (INTEGER, pode ser negativo).';

-- ------------------------------------------------------------
-- 4. black_rating_diff: VARCHAR(10) -> INTEGER
-- ------------------------------------------------------------
ALTER TABLE partida
    ALTER COLUMN black_rating_diff
        TYPE INTEGER
        USING CASE
                  WHEN black_rating_diff ~ '^[+-]?[0-9]+$' THEN black_rating_diff::INTEGER
                  ELSE NULL
              END;

COMMENT ON COLUMN partida.black_rating_diff
    IS 'Variacao de rating das brancas apos a partida (INTEGER, pode ser negativo).';

-- ------------------------------------------------------------
-- 5. date: VARCHAR(20) -> DATE
--    Formato PGN: "YYYY.MM.DD". Substitui '.' por '-'.
--    Valores com '?' ou malformados -> NULL.
-- ------------------------------------------------------------
ALTER TABLE partida
    ALTER COLUMN date
        TYPE DATE
        USING CASE
                  WHEN date IS NULL      THEN NULL
                  WHEN date LIKE '%?%'   THEN NULL
                  WHEN replace(date, '.', '-') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
                      THEN replace(date, '.', '-')::DATE
                  ELSE NULL
              END;

COMMENT ON COLUMN partida.date
    IS 'Data da partida (DATE). NULL para datas invalidas ou ausentes no PGN.';

-- ------------------------------------------------------------
-- 6. Criar utc_datetime (TIMESTAMPTZ) a partir de
--    utc_date + utc_time, depois remover as colunas originais.
--    Formato Lichess: UTCDate "YYYY.MM.DD", UTCTime "HH:MM:SS".
-- ------------------------------------------------------------
ALTER TABLE partida ADD COLUMN IF NOT EXISTS utc_datetime TIMESTAMPTZ;

UPDATE partida
SET utc_datetime =
    CASE
        WHEN utc_date IS NULL OR utc_time IS NULL     THEN NULL
        WHEN utc_date LIKE '%?%' OR utc_time LIKE '%?%' THEN NULL
        WHEN (replace(utc_date, '.', '-') || ' ' || utc_time)
                 ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}$'
            THEN (replace(utc_date, '.', '-') || ' ' || utc_time)::TIMESTAMPTZ
        ELSE NULL
    END;

COMMENT ON COLUMN partida.utc_datetime
    IS 'Data/hora UTC da partida (TIMESTAMPTZ), combinacao de UTCDate + UTCTime do Lichess.';

ALTER TABLE partida DROP COLUMN IF EXISTS utc_date;
ALTER TABLE partida DROP COLUMN IF EXISTS utc_time;

-- ------------------------------------------------------------
-- 7. fonte_pgn: nova coluna para identificar o arquivo PGN
--    de origem e detectar reimportacoes duplicadas.
-- ------------------------------------------------------------
ALTER TABLE partida ADD COLUMN IF NOT EXISTS fonte_pgn VARCHAR(255);

COMMENT ON COLUMN partida.fonte_pgn
    IS 'Nome ou hash SHA-256 do arquivo PGN de origem. Usado para evitar reimportacao duplicada.';

CREATE INDEX IF NOT EXISTS idx_partida_fonte ON partida (fonte_pgn);

-- ------------------------------------------------------------
-- 8. variante_principal: VARCHAR(500) -> TEXT
--    Variantes longas do Stockfish podem exceder 500 chars.
--    TEXT usa TOAST automaticamente no PostgreSQL.
-- ------------------------------------------------------------
ALTER TABLE lance
    ALTER COLUMN variante_principal TYPE TEXT;

COMMENT ON COLUMN lance.variante_principal
    IS 'Principal Variation (PV) do Stockfish, lances UCI separados por espaco (TEXT sem limite).';

-- ------------------------------------------------------------
-- 9. Indice em fen_depois para buscas de posicao
--    Permite queries: "em quais partidas esta posicao apareceu?"
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_lance_fen_depois ON lance (fen_depois);

COMMIT;
