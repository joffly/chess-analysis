-- =============================================================================
-- V5: ajusta game_id para VARCHAR(255)
--
-- A V4 criou a coluna com VARCHAR(64), suficiente para SHA-256 (64 chars).
-- Porém o game_id agora é preenchido com o ID nativo do PGN (tag GameId),
-- que pode ser menor (ex: "myF6FTAy" do Lichess) ou potencialmente maior.
-- VARCHAR(255) garante espaço para qualquer fonte.
-- =============================================================================

ALTER TABLE partida
    ALTER COLUMN game_id TYPE VARCHAR(255);
