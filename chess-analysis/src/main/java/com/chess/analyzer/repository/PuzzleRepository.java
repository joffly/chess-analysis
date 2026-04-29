package com.chess.analyzer.repository;

import com.chess.analyzer.entity.LanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório para busca de puzzles (lances blunder) por abertura (ECO).
 *
 * <p>Todas as queries usam JPQL com {@code JOIN FETCH} para carregamento
 * antecipado da partida, evitando N+1 e problemas de lazy-loading fora
 * de transação. A agregação por ECO é feita em Java no
 * {@link com.chess.analyzer.service.PuzzleDbService}.</p>
 */
@Repository
public interface PuzzleRepository extends JpaRepository<LanceEntity, Long> {

    /**
     * Carrega todos os lances blunder analisados junto com suas partidas.
     *
     * <p>Usado pelo {@code PuzzleDbService.listEcos()} para agregar os ECOs
     * disponíveis em memória Java, evitando queries nativas com GROUP BY que
     * apresentam comportamento inconsistente no Hibernate 7.</p>
     */
    @Query("""
            SELECT l FROM LanceEntity l
            JOIN FETCH l.partida p
            WHERE l.blunder   = true
              AND l.analisado = true
            ORDER BY p.eco ASC, p.id ASC, l.ordem ASC
            """)
    List<LanceEntity> findAllBlundersWithPartida();

    /**
     * Retorna os lances blunder de um ECO específico, com a partida pré-carregada.
     *
     * <p>Ordenado por partida e posição do lance para exibição consistente na UI.</p>
     *
     * @param eco código ECO (ex: "B20"), comparação case-insensitive
     */
    @Query("""
            SELECT l FROM LanceEntity l
            JOIN FETCH l.partida p
            WHERE UPPER(p.eco) = UPPER(:eco)
              AND l.blunder   = true
              AND l.analisado = true
            ORDER BY p.id ASC, l.ordem ASC
            """)
    List<LanceEntity> findBlundersByEco(@Param("eco") String eco);
}
