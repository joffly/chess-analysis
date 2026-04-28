package com.chess.analyzer.repository;

import com.chess.analyzer.entity.LanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório para busca de puzzles (lances blunder) por abertura (ECO).
 */
@Repository
public interface PuzzleRepository extends JpaRepository<LanceEntity, Long> {

    /**
     * Retorna lances marcados como blunder pertencentes a partidas com o ECO informado.
     * Limitado a 50 puzzles para não sobrecarregar a UI.
     */
    @Query("""
        SELECT l FROM LanceEntity l
        JOIN l.partida p
        WHERE UPPER(p.eco) = UPPER(:eco)
          AND l.blunder = true
          AND l.analisado = true
        ORDER BY l.id
        LIMIT 50
        """)
    List<LanceEntity> findBlundersByEco(@Param("eco") String eco);
}
