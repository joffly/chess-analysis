package com.chess.analyzer.model;

import java.util.List;

/**
 * DTO retornado pelo endpoint /api/board/legal-moves.
 * Contém os destinos legais para uma peça em determinado quadrado.
 */
public record LegalMovesResponse(
        String       from,       // quadrado origem, ex: "e2"
        List<String> targets,    // quadrados destino, ex: ["e3","e4"]
        boolean      promotion   // true se algum movimento é promoção
) {}
