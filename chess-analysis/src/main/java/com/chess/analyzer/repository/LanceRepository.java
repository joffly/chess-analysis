package com.chess.analyzer.repository;

import com.chess.analyzer.entity.LanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para {@link LanceEntity}.
 */
@Repository
public interface LanceRepository extends JpaRepository<LanceEntity, Long> {

    /** Retorna todos os lances de uma partida, ordenados pela sequência. */
    List<LanceEntity> findByPartidaIdOrderByOrdemAsc(Long partidaId);

    /** Conta quantos lances uma partida possui. */
    long countByPartidaId(Long partidaId);

    /** Retorna apenas os lances ainda não analisados de uma partida. */
    List<LanceEntity> findByPartidaIdAndAnalisadoFalseOrderByOrdemAsc(Long partidaId);

    /** Busca lances por notação SAN em qualquer partida (ex: para estatísticas de abertura). */
    @Query("SELECT l FROM LanceEntity l WHERE l.san = :san AND l.ordem <= :profundidade")
    List<LanceEntity> findBySanAndOrdemLessThanEqual(
            @Param("san") String san,
            @Param("profundidade") int profundidade);
}
