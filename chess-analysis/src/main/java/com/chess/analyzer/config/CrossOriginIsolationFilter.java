package com.chess.analyzer.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adiciona em todas as respostas os cabeçalhos de
 * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Cross-Origin_Resource_Policy_(CORP)">
 * cross-origin isolation</a>:
 *
 * <pre>
 * Cross-Origin-Opener-Policy:   same-origin
 * Cross-Origin-Embedder-Policy: credentialless
 * </pre>
 *
 * <h2>Por que existe</h2>
 * <p>O Stockfish multi-thread (builds {@code stockfish-18.js} e
 * {@code stockfish-18-lite.js}) usa {@link SharedArrayBuffer} para
 * paralelizar a busca em vários workers. {@code SharedArrayBuffer} só
 * fica disponível quando a página está em modo
 * <em>cross-origin isolated</em>, o que requer COOP+COEP.</p>
 *
 * <h2>Por que {@code credentialless}</h2>
 * <p>{@code COEP: require-corp} (modo "clássico") exige que TODO recurso
 * cross-origin envie {@code Cross-Origin-Resource-Policy: cross-origin}.
 * CDNs como cdn.jsdelivr.net e unpkg enviam, mas o jQuery em
 * code.jquery.com não envia — então o site quebraria.</p>
 *
 * <p>{@code COEP: credentialless} (Chrome 96+, Firefox 119+) é mais
 * permissivo: aceita recursos cross-origin SEM CORP, contanto que sejam
 * carregados sem credenciais (o que é o caso default de qualquer
 * &lt;script src="..."&gt; sem o atributo {@code crossorigin}). É a
 * escolha correta para apps que misturam mesma-origem + CDN público.</p>
 *
 * <h2>Configuração</h2>
 * <pre>chess.cross-origin-isolation=true   (default)</pre>
 *
 * <p>Desligue se algum recurso embutido (iframe, popup) parar de
 * funcionar; o custo é perder os builds multi-thread do Stockfish.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
        name         = "chess.cross-origin-isolation",
        havingValue  = "true",
        matchIfMissing = true)
public class CrossOriginIsolationFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CrossOriginIsolationFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse resp) {
            // Habilita SharedArrayBuffer (necessário p/ Stockfish multi-thread)
            resp.setHeader("Cross-Origin-Opener-Policy",   "same-origin");
            resp.setHeader("Cross-Origin-Embedder-Policy", "credentialless");
        }
        chain.doFilter(request, response);
    }

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) {
        log.info("CrossOriginIsolationFilter ativo: COOP=same-origin, COEP=credentialless " +
                 "(Stockfish multi-thread habilitado)");
    }
}
