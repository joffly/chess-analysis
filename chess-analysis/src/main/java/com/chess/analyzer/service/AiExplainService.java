package com.chess.analyzer.service;

import java.util.List;

/**
 * Abstração para serviços que explicam posições/lances de xadrez via IA.
 *
 * <p>Implementações disponíveis (ativadas via {@code ai.provider} em
 * {@code application.properties}):
 * <ul>
 *   <li>{@link LmStudioService}      — IA local (LMStudio + modelo open-source).
 *       Sem custo de rede, mas depende do servidor LMStudio rodando.</li>
 *   <li>{@link GeminiExplainService} — Google Gemini API (free tier generoso).
 *       Sem custo de processamento no backend; só faz HTTP-call.</li>
 * </ul>
 *
 * <p>O método {@link #explain(String, String, int, List)} é blocante:
 * o controller que o invoca normalmente roda em thread virtual, então
 * blocking é OK do ponto de vista de throughput.</p>
 */
public interface AiExplainService {

    /**
     * Solicita a explicação em português brasileiro de uma posição /
     * lance de xadrez, usando o resumo da análise do Stockfish.
     *
     * @param fen                  posição em FEN
     * @param moveSan              último lance jogado em SAN (pode ser vazio)
     * @param depth                profundidade da análise Stockfish
     * @param engineLineSummaries  linhas pré-formatadas do MultiPV
     *                             (ex.: "1. [+0.35] Nf3 e5 d4 …")
     * @return resultado contendo texto da explicação + provider/modelo usado
     * @throws AiExplainException se a IA não estiver disponível ou falhar
     */
    AiExplainResult explain(String fen, String moveSan, int depth, List<String> engineLineSummaries);

    /** Identificador curto do provider (ex.: "lmstudio", "gemini"). */
    default String providerName() { return getClass().getSimpleName(); }
}
