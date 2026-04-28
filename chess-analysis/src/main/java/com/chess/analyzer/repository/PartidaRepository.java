package com.chess.analyzer.repository;

import com.chess.analyzer.entity.PartidaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartidaRepository extends JpaRepository<PartidaEntity, Long> {

    /**
     * Detecta duplicidade pela combinação de arquivo PGN + índice da partida.
     * Identifica reimportações do mesmo arquivo.
     */
    Optional<PartidaEntity> findByFontePgnAndPgnIndex(String fontePgn, int pgnIndex);

    /**
     * Detecta duplicidade pelo conteúdo: mesmos jogadores, data, evento e round.
     * Identifica partidas idênticas provenientes de arquivos PGN diferentes.
     */
    @Query("""
        SELECT p FROM PartidaEntity p
        WHERE p.white  = :white
          AND p.black  = :black
          AND p.event  = :event
          AND p.round  = :round
          AND p.result = :result
        """)
    Optional<PartidaEntity> findByGameIdentity(
        @Param("white")  String white,
        @Param("black")  String black,
        @Param("event")  String event,
        @Param("round")  String round,
        @Param("result") String result
    );
}
