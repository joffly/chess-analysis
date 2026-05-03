package com.chess.analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache em memória de curta duração (TTL) para os agregados do
 * <strong>Radar de Pontos Fracos</strong>.
 *
 * <p>Os agregados sobre {@code partida} + {@code lance} podem ser caros
 * (varrem todas as partidas analisadas em memória). Como o dashboard recarrega
 * 8 endpoints simultaneamente sempre que o usuário troca o filtro de jogador
 * ou aperta "Atualizar", consultar o banco a cada chamada é desnecessário.</p>
 *
 * <h2>Características</h2>
 * <ul>
 *   <li>{@link ConcurrentHashMap} thread-safe para múltiplas requisições simultâneas.</li>
 *   <li>TTL configurável via {@code chess.radar.cache-ttl-ms} (default 60_000 ms).</li>
 *   <li>Chave hierárquica ({@code summary:fjoffly}, {@code openings:fjoffly}…)
 *       que permite invalidação em massa por prefixo.</li>
 *   <li>{@link #invalidateAll()} usado pelo job batch após sincronizar partidas.</li>
 * </ul>
 *
 * <p>Optei por um cache <em>caseiro</em> em vez de Spring Cache + Caffeine para
 * evitar uma nova dependência no classpath; a implementação é suficiente para
 * o volume de leituras do dashboard.</p>
 */
@Service
public class RadarCacheService {

    private static final Logger log = LoggerFactory.getLogger(RadarCacheService.class);

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;

    public RadarCacheService(@Value("${chess.radar.cache-ttl-ms:60000}") long ttlMs) {
        this.ttlMs = ttlMs > 0 ? ttlMs : 60_000;
        log.info("RadarCacheService inicializado com TTL = {} ms", this.ttlMs);
    }

    /**
     * Retorna o valor cacheado para {@code key} ou computa via {@code supplier}.
     * Entradas expiradas são tratadas como ausentes e recalculadas.
     */
    @SuppressWarnings("unchecked")
    public <T> T computeIfFresh(String key, Supplier<T> supplier) {
        long now = System.currentTimeMillis();
        Entry e = cache.get(key);
        if (e != null && (now - e.timestamp) < ttlMs) {
            log.debug("cache HIT  key='{}', age={}ms", key, now - e.timestamp);
            return (T) e.value;
        }

        log.debug("cache MISS key='{}'", key);
        T fresh = supplier.get();
        cache.put(key, new Entry(fresh, now));
        return fresh;
    }

    /** Invalida todas as entradas (após sincronização batch ou ações de escrita). */
    public void invalidateAll() {
        int n = cache.size();
        cache.clear();
        log.info("Radar cache invalidado ({} entrada(s) removida(s)).", n);
    }

    /** Estatísticas para diagnóstico. */
    public int size() {
        return cache.size();
    }

    public long getTtlMs() {
        return ttlMs;
    }

    /** Entrada interna (valor + timestamp de criação). */
    private record Entry(Object value, long timestamp) {}
}
