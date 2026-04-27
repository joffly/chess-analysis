-- ============================================================
--  V3 - Adiciona campo blunder na tabela lance
--  Projeto : Chess Analyzer
--  Banco   : PostgreSQL 14+
--  Flyway  : V3__add_blunder_lance.sql
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. blunder: nova coluna BOOLEAN NOT NULL DEFAULT FALSE.
--    Marcada como TRUE quando o Stockfish classifica o lance
--    como blunder (queda de avaliacao >= 2 peoes em relacao
--    ao melhor lance disponivel na posicao).
--    DEFAULT FALSE garante retrocompatibilidade com lances
--    ainda nao analisados.
-- ------------------------------------------------------------
ALTER TABLE lance
    ADD COLUMN IF NOT EXISTS blunder BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN lance.blunder
    IS 'TRUE quando o lance e classificado como blunder pelo Stockfish (queda >= 2 peoes). DEFAULT FALSE.';

-- ------------------------------------------------------------
-- 2. Indice parcial: indexa apenas as linhas onde blunder=TRUE.
--    Lances normais (blunder=FALSE) sao ignorados pelo indice,
--    reduzindo tamanho e custo de manutencao.
--    Caso de uso: listar todos os blunders de uma partida.
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_lance_blunder
    ON lance (partida_id)
    WHERE blunder = TRUE;

COMMIT;
