package com.chess.analyzer.service;

/**
 * Resultado da chamada {@link AiExplainService#explain}.
 *
 * @param text     texto da explicação (3 parágrafos curtos em PT-BR)
 * @param provider identificador do provider (ex.: "gemini", "lmstudio")
 * @param model    identificador do modelo realmente usado (pode ser
 *                 diferente do configurado caso tenha caído no fallback)
 */
public record AiExplainResult(String text, String provider, String model) {
}
