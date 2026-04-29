package com.chess.analyzer.repository;

import com.chess.analyzer.entity.LanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LanceRepository extends JpaRepository<LanceEntity, Long> {

    @Modifying
    @Query("DELETE FROM LanceEntity l WHERE l.partida.id = :partidaId")
    void deleteByPartidaId(@Param("partidaId") Long partidaId);

    /**
     * Busca o lance imediatamente anterior ao blunder na mesma partida.
     * Usado pelo {@code PuzzleDbService} para obter a avaliação antes do blunder (evalBefore).
     *
     * @param partidaId ID da partida
     * @param ordem     posição sequencial do lance anterior (= ordemBlunder - 1)
     */
    @Query("SELECT l FROM LanceEntity l WHERE l.partida.id = :partidaId AND l.ordem = :ordem")
    Optional<LanceEntity> findByPartidaIdAndOrdem(@Param("partidaId") Long partidaId,
                                                   @Param("ordem")     int    ordem);
}
