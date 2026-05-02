package com.chess.analyzer.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * Serviço que se comunica com o LMStudio local via OpenAI-compat API.
 *
 * <h2>Endpoint</h2>
 * <pre>POST {baseUrl}/v1/chat/completions</pre>
 *
 * <p>Este endpoint separa automaticamente o raciocínio interno (thinking) da
 * resposta final para modelos Qwen3 — a resposta útil fica em
 * {@code choices[0].message.content} e o thinking em
 * {@code choices[0].message.reasoning_content} (ignorado).</p>
 *
 * <h2>Configuração (application.properties)</h2>
 * <pre>
 * chess.lmstudio-url=http://localhost:1234
 * chess.lmstudio-model=qwen_qwen3.6-35b-a3b
 * </pre>
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "lmstudio", matchIfMissing = false)
public class LmStudioService implements AiExplainService {

    private static final Logger log = LoggerFactory.getLogger(LmStudioService.class);

    @Override
    public String providerName() { return "lmstudio"; }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 120_000;

    @Value("${chess.lmstudio-url:http://localhost:1234}")
    private String baseUrl;

    @Value("${chess.lmstudio-model:qwen_qwen3.6-35b-a3b}")
    private String model;

    /**
     * Solicita ao LMStudio uma explicação em português de uma posição/lance de xadrez.
     */
    @Override
    public AiExplainResult explain(String fen, String moveSan, int depth, List<String> engineLineSummaries) {

        String systemContent =
            "Você é um treinador de xadrez objetivo e direto. REGRAS OBRIGATÓRIAS: " +
            "(1) Responda SOMENTE em português brasileiro. " +
            "(2) NÃO escreva raciocínio interno, rascunhos ou passos de pensamento. " +
            "(3) Resposta MÁXIMA de 120 palavras, sem introduções longas. " +
            "(4) Use exatamente 3 parágrafos curtos numerados conforme solicitado.";

        StringBuilder userContent = new StringBuilder();
        if (moveSan != null && !moveSan.isBlank()) {
            userContent.append("Lance jogado: ").append(moveSan).append("\n");
        }
        userContent.append("FEN: ").append(fen).append("\n");
        if (!engineLineSummaries.isEmpty()) {
            userContent.append("Stockfish prof.").append(depth).append(": ");
            engineLineSummaries.forEach(l -> userContent.append(l).append(" | "));
            userContent.append("\n");
        }
        userContent.append("\nResponda com exatamente 3 parágrafos curtos em português:\n");
        userContent.append("1) Lance bom/médio/ruim e motivo (1-2 frases)\n");
        userContent.append("2) Ideia da melhor linha da engine (1-2 frases)\n");
        userContent.append("3) Tema tático/estratégico da posição (1 frase)\n");
        userContent.append("NÃO repita o FEN. NÃO escreva em inglês. NÃO mostre processo de pensamento.");

        // Formato OpenAI-compat — separa thinking de content para modelos Qwen3
        Map<String, Object> sysMsg  = new LinkedHashMap<>();
        sysMsg.put("role",    "system");
        sysMsg.put("content", systemContent);

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role",    "user");
        userMsg.put("content", userContent.toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model",      model);
        payload.put("messages",  List.of(sysMsg, userMsg));
        payload.put("stream",    false);
        payload.put("max_tokens", 2048);   // garante espaço para thinking + resposta

        String endpoint = baseUrl + "/v1/chat/completions";
        log.debug("LMStudio explain → {} (model={})", endpoint, model);

        try {
            String requestBody = MAPPER.writeValueAsString(payload);
            log.debug("LMStudio request body ({} chars)", requestBody.length());

            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept",       "application/json");
            conn.setRequestProperty("Connection",   "close");

            byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bodyBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int status = conn.getResponseCode();
            log.debug("LMStudio HTTP status: {}", status);

            String rawBody;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    status >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8))) {
                rawBody = br.lines().collect(Collectors.joining("\n"));
            }
            conn.disconnect();

            log.debug("LMStudio raw response: {}", rawBody);

            if (status < 200 || status >= 300) {
                throw new LmStudioException(
                    "LMStudio retornou HTTP " + status + ". Corpo: " + abbreviate(rawBody, 300));
            }
            if (rawBody == null || rawBody.isBlank()) {
                throw new LmStudioException("LMStudio retornou corpo vazio.");
            }

            String text = extractContent(rawBody);
            return new AiExplainResult(text, providerName(), model);

        } catch (java.net.ConnectException e) {
            log.warn("LMStudio não acessível em {}: {}", baseUrl, e.getMessage());
            throw new LmStudioException(
                "LMStudio não está disponível em " + baseUrl +
                ". Verifique se o servidor local está rodando.", e);
        } catch (java.net.SocketTimeoutException e) {
            log.warn("LMStudio timeout em {}: {}", baseUrl, e.getMessage());
            throw new LmStudioException(
                "LMStudio não respondeu em " + (READ_TIMEOUT_MS / 1000) + " segundos.", e);
        } catch (LmStudioException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar LMStudio: {}", e.getMessage(), e);
            throw new LmStudioException("Erro ao comunicar com LMStudio: " + e.getMessage(), e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Extrai o texto útil da resposta.
     *
     * <p>Prioridade:
     * <ol>
     *   <li>OpenAI-compat {@code choices[0].message.content} (ignora reasoning_content)</li>
     *   <li>LMStudio nativo {@code output[].content} filtrando type != "reasoning"</li>
     *   <li>Campos planos: output / response / text / content</li>
     * </ol>
     */
    private static String extractContent(String rawBody) throws Exception {
        JsonNode root = MAPPER.readTree(rawBody);

        List<String> keys = new ArrayList<>();
        root.fieldNames().forEachRemaining(keys::add);
        log.debug("LMStudio response keys: {}", keys);

        // 1. OpenAI-compat: choices[0].message.content  (thinking fica em reasoning_content)
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode msg = choices.get(0).path("message");

            // 1a. content com resposta real
            String c = msg.path("content").asText("").strip();
            if (!c.isEmpty()) {
                log.debug("LMStudio: extraído de choices[0].message.content.");
                return stripThinking(c);
            }

            // 1b. content vazio (finish_reason=length): extrai do reasoning_content como fallback
            String rc = msg.path("reasoning_content").asText("").strip();
            if (!rc.isEmpty()) {
                log.debug("LMStudio: content vazio, tentando extrair do reasoning_content.");
                String extracted = stripThinking(rc);
                if (!extracted.isBlank()) return extracted;
            }
        }

        // 2. LMStudio nativo: output[type=message].content
        JsonNode outputNode = root.path("output");
        if (outputNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : outputNode) {
                String type = item.path("type").asText("message");
                if (type.equals("reasoning") || type.equals("thinking")) {
                    log.debug("LMStudio: ignorando bloco tipo '{}'.", type);
                    continue;
                }
                String c = item.path("content").asText("").strip();
                if (!c.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(c);
                }
            }
            if (!sb.isEmpty()) {
                log.debug("LMStudio: extraído de output[type=message].content.");
                return stripThinking(sb.toString());
            }
        }

        // 3. Campos planos
        for (String field : new String[]{ "output", "response", "text", "content" }) {
            JsonNode node = root.path(field);
            if (node.isTextual() && !node.asText().isBlank()) {
                log.debug("LMStudio: extraído do campo plano '{}'.", field);
                return stripThinking(node.asText());
            }
        }

        throw new LmStudioException(
            "Resposta do LMStudio não contém texto reconhecido. " +
            "Campos: " + keys + " — veja log DEBUG para o corpo completo.");
    }

    /** Remove blocos de chain-of-thought que escapem para o campo content. */
    private static String stripThinking(String text) {
        if (text == null || text.isBlank()) return "";

        // Remove <think>...</think>
        String result = text.replaceAll("(?s)<think>.*?</think>", "").strip();

        if (!looksLikeThinking(result)) return result;

        // Procura início da resposta em português
        for (String marker : new String[]{
                "\n1) ", "\n1. ", "\n**1", "\n**Lance", "\n**Resposta",
                "\nLance ", "\nO lance", "\nEm português", "\n---" }) {
            int idx = result.indexOf(marker);
            if (idx != -1) {
                String candidate = result.substring(idx).strip();
                if (!looksLikeThinking(candidate)) return candidate;
            }
        }

        // Fallback: último bloco longo que não seja thinking
        String[] blocks = result.split("\n\\s*\n");
        for (int i = blocks.length - 1; i >= 0; i--) {
            String block = blocks[i].strip();
            if (!block.isBlank() && !looksLikeThinking(block) && block.length() > 40) return block;
        }

        return result.strip();
    }

    private static boolean looksLikeThinking(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.strip().toLowerCase();
        return lower.startsWith("here's a thinking") || lower.startsWith("let me think")
            || lower.startsWith("let me analyze")    || lower.startsWith("thinking process")
            || lower.startsWith("step 1:")            || lower.startsWith("step-by-step")
            || lower.startsWith("**analyze")          || lower.startsWith("**step");
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "(null)";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ── Exception interna ────────────────────────────────────────────────────

    public static class LmStudioException extends AiExplainException {
        public LmStudioException(String message) { super(message); }
        public LmStudioException(String message, Throwable cause) { super(message, cause); }
    }
}
