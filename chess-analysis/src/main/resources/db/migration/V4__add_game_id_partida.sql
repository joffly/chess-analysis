-- =============================================================================
-- V4: adiciona coluna game_id à tabela partida
--
-- game_id é um SHA-256 hex (64 chars) calculado sobre os campos identitários
-- da partida: white|black|event|site|date|round|result|initial_fen
--
-- Substitui a checagem dupla anterior (fonte_pgn+pgn_index / game identity)
-- por um único lookup determinístico.
-- =============================================================================

-- 1. Habilita a extensão pgcrypto (necessária para digest/SHA-256).
--    Se já existir, o IF NOT EXISTS evita erro.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Adiciona a coluna com DEFAULT vazio para não violar NOT NULL
--    nos registros já existentes.
ALTER TABLE partida
    ADD COLUMN game_id VARCHAR(64) NOT NULL DEFAULT '';

-- 3. Popula game_id em todos os registros existentes.
--    Usa a mesma lógica do GameData.getGameId():
--      SHA-256( white|black|event|site|date|round|result|initial_fen )
--    Campos nulos são substituídos por '?' para manter consistência
--    com a normalização feita em Java.
UPDATE partida
SET game_id = encode(
    digest(
        COALESCE(NULLIF(TRIM(white),  ''), '?') || '|' ||
        COALESCE(NULLIF(TRIM(black),  ''), '?') || '|' ||
        COALESCE(NULLIF(TRIM(event),  ''), '?') || '|' ||
        COALESCE(NULLIF(TRIM(site),   ''), '?') || '|' ||
        COALESCE(NULLIF(CAST(date AS TEXT), ''), '?') || '|' ||
        COALESCE(NULLIF(TRIM(round),  ''), '?') || '|' ||
        COALESCE(NULLIF(TRIM(result), ''), '?') || '|' ||
        COALESCE(NULLIF(TRIM(initial_fen), ''), '?'),
        'sha256'
    ),
    'hex'
);

-- 4. Remove o DEFAULT temporário (a partir daqui a coluna é gerenciada
--    exclusivamente pela aplicação).
ALTER TABLE partida
    ALTER COLUMN game_id DROP DEFAULT;

-- 5. Adiciona a UNIQUE constraint.
ALTER TABLE partida
    ADD CONSTRAINT uk_partida_game_id UNIQUE (game_id);

-- 6. Index explícito para acelerar o lookup por game_id
--    (a UK já cria um index, mas nomear explícito facilita monitoring).
CREATE INDEX IF NOT EXISTS idx_partida_game_id ON partida (game_id);
