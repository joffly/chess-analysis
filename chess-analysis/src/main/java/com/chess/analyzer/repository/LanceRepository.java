package com.chess.analyzer.repository;

import com.chess.analyzer.entity.LanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LanceRepository extends JpaRepository<LanceEntity, Long> {

    @Modifying
    @Query("DELETE FROM LanceEntity l WHERE l.partida.id = :partidaId")
    void deleteByPartidaId(@Param("partidaId") Long partidaId);
}
