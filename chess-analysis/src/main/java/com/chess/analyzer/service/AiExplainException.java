package com.chess.analyzer.service;

/**
 * Exceção lançada por implementações de {@link AiExplainService} quando
 * a IA não está disponível, retorna erro ou produz resposta inválida.
 */
public class AiExplainException extends RuntimeException {

    public AiExplainException(String message) {
        super(message);
    }

    public AiExplainException(String message, Throwable cause) {
        super(message, cause);
    }
}
