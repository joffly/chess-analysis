package com.chess.analyzer.repository;

import com.chess.analyzer.entity.PartidaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartidaRepository extends JpaRepository<PartidaEntity, Long> {

    /**
     * Busca uma partida pelo seu identificador estável de negócio.
     *
     * <p>{@code gameId} é um SHA-256 hex calculado sobre os campos identitários
     * da partida — é o único critério de upsert utilizado pelo
     * {@link com.chess.analyzer.service.PartidaSaveService}.</p>
     */
    Optional<PartidaEntity> findByGameId(String gameId);
}
