package com.chess.analyzer.repository;

import com.chess.analyzer.entity.PartidaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para {@link PartidaEntity}.
 * O Spring Data gera a implementação em tempo de execução.
 */
@Repository
public interface PartidaRepository extends JpaRepository<PartidaEntity, Long> {

    /** Busca partidas por jogador das brancas (case-insensitive). */
    List<PartidaEntity> findByWhiteIgnoreCase(String white);

    /** Busca partidas por jogador das pretas (case-insensitive). */
    List<PartidaEntity> findByBlackIgnoreCase(String black);

    /** Busca partidas em que um jogador participou (brancas ou pretas). */
    @Query("SELECT p FROM PartidaEntity p WHERE "
         + "LOWER(p.white) = LOWER(:player) OR LOWER(p.black) = LOWER(:player)")
    List<PartidaEntity> findByPlayer(@Param("player") String player);

    /** Verifica se uma partida com o mesmo índice PGN já foi importada. */
    boolean existsByPgnIndex(int pgnIndex);

    /** Lista partidas por resultado. */
    List<PartidaEntity> findByResult(String result);

    /** Busca partidas por abertura (match parcial, case-insensitive). */
    List<PartidaEntity> findByOpeningContainingIgnoreCase(String opening);
}
