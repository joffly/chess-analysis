package com.chess.analyzer.exception;

/**
 * Exceção lançada quando a comunicação com a API do Lichess falha.
 *
 * <p>Cobre cenários como usuário não encontrado (404), limite de requisições
 * atingido (429) ou qualquer falha de rede/HTTP.</p>
 */
public class LichessApiException extends RuntimeException {

    public LichessApiException(String message) {
        super(message);
    }

    public LichessApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
