package com.chess.analyzer.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementação de {@link AiExplainService} usando a API REST do Google Gemini.
 *
 * <h2>Endpoint</h2>
 * 
 * <pre>
 * POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
 * </pre>
 *
 * <h2>Por que Gemini</h2>
 * <p>
 * Free tier generoso (na época: ~15 req/min e 1500 req/dia para
 * gemini-1.5-flash / gemini-2.5-flash), sem necessidade de cartão de crédito, e
 * funciona bem em português brasileiro. Como tudo roda na infraestrutura do
 * Google, o backend só faz HTTP-call (sem custo de CPU).
 * </p>
 *
 * <h2>Configuração (application.properties)</h2>
 * 
 * <pre>
 * ai.provider=gemini
 * ai.gemini.api-key=AIza...      (gere em https://aistudio.google.com/apikey)
 * ai.gemini.model=gemini-2.5-flash
 * </pre>
 *
 * <p>
 * Bean ativado quando {@code ai.provider=gemini}.
 * </p>
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiExplainService implements AiExplainService {

	private static final Logger log = LoggerFactory.getLogger(GeminiExplainService.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 60_000;

	@Value("${ai.gemini.api-key:${GEMINI_API_KEY:}}")
	private String apiKey;

	@Value("${ai.gemini.model:gemini-2.5-flash}")
	private String model;

	/**
	 * Modelo de fallback se o configurado não responder (ex.: 404 do Gemini 2.5
	 * fora da disponibilidade ou rate-limit).
	 */
	@Value("${ai.gemini.fallback-model:gemini-1.5-flash}")
	private String fallbackModel;

	@Override
	public String providerName() {
		return "gemini";
	}

	@Override
	public AiExplainResult explain(String fen, String moveSan, int depth, List<String> engineLineSummaries) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new AiExplainException("ai.gemini.api-key não configurado. Gere uma chave gratuita em "
					+ "https://aistudio.google.com/apikey e adicione em application.properties.");
		}

		String prompt = buildPrompt(fen, moveSan, depth, engineLineSummaries);

		try {
			String text = callGemini(model, prompt);
			return new AiExplainResult(text, providerName(), model);
		} catch (AiExplainException primaryErr) {
			// Fallback: tenta o modelo alternativo se o primeiro falhar com 404/429
			String msg = primaryErr.getMessage();
			boolean shouldFallback = msg != null
					&& (msg.contains("HTTP 404") || msg.contains("HTTP 429") || msg.contains("HTTP 503"));
			if (!shouldFallback || fallbackModel == null || fallbackModel.equals(model)) {
				throw primaryErr;
			}
			log.warn("Gemini {} falhou ({}); tentando fallback {}", model, msg, fallbackModel);
			try {
				String text = callGemini(fallbackModel, prompt);
				return new AiExplainResult(text, providerName(), fallbackModel);
			} catch (AiExplainException fallbackErr) {
				throw new AiExplainException(
						"Falha tanto em " + model + " quanto em " + fallbackModel + ": " + fallbackErr.getMessage(),
						fallbackErr);
			}
		}
	}

	// ── Internos ────────────────────────────────────────────────────────────

	private String callGemini(String modelToUse, String prompt) {
		String endpoint = BASE_URL + modelToUse + ":generateContent?key=" + apiKey;
		log.debug("Gemini explain → {} (model={})", BASE_URL + modelToUse, modelToUse);

		// Payload Gemini: { contents: [{role:"user", parts:[{text:"..."}]}],
		// generationConfig: {...} }
		Map<String, Object> userPart = Map.of("text", prompt);
		Map<String, Object> userContent = Map.of("role", "user", "parts", List.of(userPart));
		Map<String, Object> generationConfig = new LinkedHashMap<>();
		generationConfig.put("temperature", 0.4);
		generationConfig.put("maxOutputTokens", 2048);
		generationConfig.put("topP", 0.9);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("contents", List.of(userContent));
		payload.put("generationConfig", generationConfig);

		try {
			String requestBody = MAPPER.writeValueAsString(payload);

			HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			conn.setRequestProperty("Accept", "application/json");

			byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
			conn.setFixedLengthStreamingMode(bodyBytes.length);
			try (OutputStream os = conn.getOutputStream()) {
				os.write(bodyBytes);
				os.flush();
			}

			int status = conn.getResponseCode();
			String rawBody;
			try (BufferedReader br = new BufferedReader(new InputStreamReader(
					status >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
				rawBody = br.lines().collect(Collectors.joining("\n"));
			}
			conn.disconnect();

			log.debug("Gemini HTTP {}; body chars={}", status, rawBody == null ? 0 : rawBody.length());

			if (status < 200 || status >= 300) {
				throw new AiExplainException("Gemini retornou HTTP " + status + " (" + extractError(rawBody) + ")");
			}
			if (rawBody == null || rawBody.isBlank()) {
				throw new AiExplainException("Gemini retornou corpo vazio.");
			}

			return extractText(rawBody);

		} catch (java.net.UnknownHostException e) {
			throw new AiExplainException("Não foi possível resolver o DNS de generativelanguage.googleapis.com. "
					+ "Verifique a conexão de internet do servidor.", e);
		} catch (java.net.SocketTimeoutException e) {
			throw new AiExplainException("Gemini não respondeu em " + (READ_TIMEOUT_MS / 1000) + " segundos.", e);
		} catch (AiExplainException e) {
			throw e;
		} catch (Exception e) {
			log.error("Erro inesperado ao chamar Gemini: {}", e.getMessage(), e);
			throw new AiExplainException("Erro ao comunicar com Gemini: " + e.getMessage(), e);
		}
	}

	/**
	 * Extrai o texto da resposta Gemini.
	 *
	 * <p>
	 * Formato esperado:
	 * </p>
	 * 
	 * <pre>
	 * {
	 *   "candidates": [{
	 *     "content": { "parts": [{ "text": "..." }], "role": "model" },
	 *     "finishReason": "STOP"
	 *   }]
	 * }
	 * </pre>
	 */
	private static String extractText(String rawBody) {
		try {
			JsonNode root = MAPPER.readTree(rawBody);
			JsonNode candidates = root.path("candidates");
			if (!candidates.isArray() || candidates.isEmpty()) {
				// Pode ter sido bloqueado por safety: promptFeedback.blockReason
				String block = root.path("promptFeedback").path("blockReason").asText("");
				if (!block.isEmpty()) {
					throw new AiExplainException("Gemini bloqueou a resposta (" + block + ")");
				}
				throw new AiExplainException("Resposta Gemini sem 'candidates'.");
			}

			JsonNode cand = candidates.get(0);
			String finish = cand.path("finishReason").asText("");
			JsonNode parts = cand.path("content").path("parts");
			if (parts.isArray() && !parts.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				parts.forEach(p -> {
					String t = p.path("text").asText("");
					if (!t.isEmpty())
						sb.append(t);
				});
				String text = sb.toString().strip();
				if (!text.isEmpty())
					return text;
			}

			if ("SAFETY".equalsIgnoreCase(finish) || "RECITATION".equalsIgnoreCase(finish)) {
				throw new AiExplainException("Gemini interrompeu a geração (" + finish + ")");
			}
			throw new AiExplainException("Resposta Gemini sem texto utilizável (finishReason=" + finish + ")");
		} catch (AiExplainException e) {
			throw e;
		} catch (Exception e) {
			throw new AiExplainException("Falha ao parsear resposta Gemini: " + e.getMessage(), e);
		}
	}

	/** Extrai a mensagem de erro estruturada do Gemini, se houver. */
	private static String extractError(String rawBody) {
		if (rawBody == null)
			return "(sem corpo)";
		try {
			JsonNode root = MAPPER.readTree(rawBody);
			String msg = root.path("error").path("message").asText("");
			if (!msg.isEmpty())
				return msg;
		} catch (Exception ignored) {
			/* fall-through */ }
		return rawBody.length() > 200 ? rawBody.substring(0, 200) + "…" : rawBody;
	}

	/**
	 * Monta o prompt enviado ao Gemini. Mesmo formato/estilo do LMStudio para
	 * manter consistência da resposta entre providers.
	 */
	private static String buildPrompt(String fen, String moveSan, int depth, List<String> engineLineSummaries) {
		StringBuilder p = new StringBuilder();
		p.append("Você é um treinador de xadrez objetivo e direto. ")
				.append("Responda SOMENTE em português brasileiro, máximo 120 palavras, ")
				.append("em exatamente 3 parágrafos curtos numerados.\n\n");

		if (moveSan != null && !moveSan.isBlank()) {
			p.append("Lance jogado: ").append(moveSan).append("\n");
		}
		p.append("FEN: ").append(fen).append("\n");
		if (engineLineSummaries != null && !engineLineSummaries.isEmpty()) {
			p.append("Stockfish prof.").append(depth).append(": ");
			for (String line : engineLineSummaries)
				p.append(line).append(" | ");
			p.append("\n");
		}
		p.append("\nResponda com exatamente 3 parágrafos:\n").append("1) Lance bom/médio/ruim e motivo (1-2 frases).\n")
				.append("2) Ideia da melhor linha da engine (1-2 frases).\n")
				.append("3) Tema tático/estratégico da posição (1 frase).\n")
				.append("NÃO repita o FEN. NÃO escreva em inglês. NÃO mostre raciocínio.");

		return p.toString();
	}
}
